package evcharging

import java.time.LocalDateTime

// Connector types: AC (slow charging) or DC (fast charging)
sealed trait ConnectorType { def name: String }
case object AC extends ConnectorType { def name = "AC" }
case object DC extends ConnectorType { def name = "DC" }

// Session status tracking
sealed trait SessionStatus { def name: String }
case object ACTIVE extends SessionStatus { def name = "ACTIVE" }
case object COMPLETED extends SessionStatus { def name = "COMPLETED" }

// Driver model - represents a person who wants to charge their vehicle
case class Driver(id: String = IdGenerator.next("DRV"), name: String, email: String, phone: String)

// Vehicle model - belongs to a driver
case class Vehicle(id: String = IdGenerator.next("VEH"), driverId: String, make: String, model: String, regNumber: String)

// Connector model - at a charging station, can be AC or DC
case class Connector(id: String = IdGenerator.next("CON"), stationId: String, connectorType: ConnectorType, power: Double, isActive: Boolean = true)

// Station model - has one or more connectors at a location
case class Station(id: String = IdGenerator.next("STN"), name: String, latitude: Double, longitude: Double, connectors: Map[String, Connector] = Map())

// Charging session model - records a driver's charging activity
case class ChargingSession(
  id: String = IdGenerator.next("SES"),
  driverId: String,
  stationId: String,
  connectorId: String,
  connectorType: ConnectorType,
  startTime: LocalDateTime = LocalDateTime.now(),
  endTime: Option[LocalDateTime] = None,
  energyDelivered: Option[Double] = None,
  baseCost: Option[Double] = None,
  discount: Option[Double] = None,
  finalCost: Option[Double] = None,
  promoCode: Option[String] = None,
  status: SessionStatus = ACTIVE
)

// Promo code model - represents discounts
case class PromoCode(code: String, discountPercent: Double, maxUses: Option[Int] = None, usedCount: Int = 0, isActive: Boolean = true)

// Tariff slab for tiered pricing
case class TariffSlab(kWhStart: Double, kWhEnd: Option[Double], ratePerKWh: Double)

// Tariff model - defines pricing rules
case class Tariff(connectorType: ConnectorType, minimumCharge: Double, slabs: List[TariffSlab])
