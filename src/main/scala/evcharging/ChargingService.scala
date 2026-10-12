package evcharging

import java.time.LocalDateTime

// A connector plus its live status, used for listing connectors at a station.
case class ConnectorView(id: String, connectorType: String, power: Double, inService: Boolean, free: Boolean)

// Core business logic: connects the HTTP layer, the billing engine, and storage.
class ChargingService(storage: Storage) {

  // Add a connector (AC or DC) to a station.
  def addConnector(stationId: String, connectorType: ConnectorType, power: Double): Either[String, Connector] =
    storage.getStation(stationId) match {
      case None => Left("Station not found")
      case Some(station) =>
        val connector = Connector(stationId = stationId, connectorType = connectorType, power = power)
        storage.updateStation(station.copy(connectors = station.connectors + (connector.id -> connector)))
        Right(connector)
    }

  // List all connectors at a station, marking which are in service and free.
  def listConnectors(stationId: String): Either[String, List[ConnectorView]] =
    storage.getStation(stationId) match {
      case None => Left("Station not found")
      case Some(station) =>
        val busy = activeConnectorIds(stationId)
        Right(station.connectors.values.toList.map(c =>
          ConnectorView(c.id, c.connectorType.name, c.power, c.isActive, !busy.contains(c.id))))
    }

  // Distance between two coordinates in km (Haversine formula).
  private def distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double = {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    R * 2 * Math.asin(Math.sqrt(a))
  }

  // Stations within the given radius of a point.
  def findNearbyStations(latitude: Double, longitude: Double, radiusKm: Double): List[Station] =
    storage.getAllStations.filter(s => distanceKm(latitude, longitude, s.latitude, s.longitude) <= radiusKm)

  // IDs of connectors currently busy with an active session at a station.
  private def activeConnectorIds(stationId: String): Set[String] =
    storage.getStationSessions(stationId).filter(_.status == ACTIVE).map(_.connectorId).toSet

  // Find a free connector of the requested type; if AC is full, fall back to a free DC connector.
  private def findFreeConnector(stationId: String, preferredType: ConnectorType): Option[Connector] =
    storage.getStation(stationId).flatMap { station =>
      val busy = activeConnectorIds(stationId)
      def freeOfType(t: ConnectorType) = station.connectors.values.find(c => c.isActive && c.connectorType == t && !busy.contains(c.id))
      freeOfType(preferredType).orElse(if (preferredType == AC) freeOfType(DC) else None)
    }

  // Start a session: validate, allocate a connector, apply any promo, and save.
  def startChargingSession(driverId: String, stationId: String, preferredType: ConnectorType, promoCode: Option[String] = None): Either[String, ChargingSession] = {
    if (storage.getDriver(driverId).isEmpty) return Left("Driver not found")
    if (storage.getStation(stationId).isEmpty) return Left("Station not found")

    findFreeConnector(stationId, preferredType) match {
      case None => Left("No compatible connector available")
      case Some(connector) =>
        // Bill at the REQUESTED type's tariff (so AC-on-DC is charged the cheaper AC rate).
        val baseCost = BillingEngine.getTariff(preferredType).minimumCharge
        val discount = validPromoDiscount(promoCode)
        val finalCost = if (discount > 0) BillingEngine.applyDiscount(baseCost, discount) else baseCost

        val session = ChargingSession(
          driverId = driverId,
          stationId = stationId,
          connectorId = connector.id,
          connectorType = preferredType,
          baseCost = Some(baseCost),
          discount = if (discount > 0) Some(discount) else None,
          finalCost = Some(finalCost),
          promoCode = promoCode.filter(_ => discount > 0)
        )
        storage.addSession(session)
        promoCode.flatMap(storage.getPromoCode).foreach(p => storage.updatePromoCode(p.copy(usedCount = p.usedCount + 1)))
        Right(session)
    }
  }

  // Returns the discount % for a valid, usable promo code, else 0.
  private def validPromoDiscount(promoCode: Option[String]): Double =
    promoCode.flatMap(storage.getPromoCode) match {
      case Some(p) if p.isActive && p.usedCount < p.maxUses.getOrElse(Int.MaxValue) => p.discountPercent
      case _ => 0.0
    }

  // End a session: calculate the tiered bill for the energy delivered and mark it complete.
  def endChargingSession(sessionId: String, energyKwh: Double): Either[String, ChargingSession] =
    storage.getSession(sessionId) match {
      case None => Left("Session not found")
      case Some(s) if s.status != ACTIVE => Left("Session is not active")
      case _ if energyKwh < 0 => Left("Energy cannot be negative")
      case Some(s) =>
        val baseCost = BillingEngine.calculateCost(energyKwh, BillingEngine.getTariff(s.connectorType))
        val finalCost = s.discount.map(BillingEngine.applyDiscount(baseCost, _)).getOrElse(baseCost)
        val completed = s.copy(
          endTime = Some(LocalDateTime.now()),
          energyDelivered = Some(energyKwh),
          baseCost = Some(baseCost),
          finalCost = Some(finalCost),
          status = COMPLETED
        )
        storage.updateSession(completed)
        Right(completed)
    }

  // Take a connector out of service (won't be offered to new sessions).
  def deactivateConnector(connectorId: String): Either[String, Connector] = setConnectorStatus(connectorId, isActive = false)

  // Bring a connector back into service.
  def activateConnector(connectorId: String): Either[String, Connector] = setConnectorStatus(connectorId, isActive = true)

  // Shared helper to flip a connector's in-service flag.
  private def setConnectorStatus(connectorId: String, isActive: Boolean): Either[String, Connector] =
    storage.getAllStations.find(_.connectors.contains(connectorId)) match {
      case None => Left("Connector not found")
      case Some(station) =>
        val updated = station.connectors(connectorId).copy(isActive = isActive)
        storage.updateStation(station.copy(connectors = station.connectors + (connectorId -> updated)))
        Right(updated)
    }

  // Create a promo code (0-100% discount, unique code).
  def createPromoCode(code: String, discountPercent: Double, maxUses: Option[Int] = None): Either[String, PromoCode] = {
    if (discountPercent < 0 || discountPercent > 100) Left("Discount must be 0-100%")
    else if (storage.getPromoCode(code).isDefined) Left("Promo code already exists")
    else Right(storage.addPromoCode(PromoCode(code = code, discountPercent = discountPercent, maxUses = maxUses)))
  }
}
