package evcharging

import scala.collection.mutable

// In-memory storage: everything lives in HashMaps and is lost when the server stops.
class Storage {
  private val drivers = mutable.Map[String, Driver]()
  private val vehicles = mutable.Map[String, Vehicle]()
  private val stations = mutable.Map[String, Station]()
  private val sessions = mutable.Map[String, ChargingSession]()
  private val promoCodes = mutable.Map[String, PromoCode]()

  // Drivers
  def addDriver(driver: Driver): Driver = { drivers(driver.id) = driver; driver }
  def getDriver(id: String): Option[Driver] = drivers.get(id)

  // Vehicles
  def addVehicle(vehicle: Vehicle): Vehicle = { vehicles(vehicle.id) = vehicle; vehicle }

  // Stations
  def addStation(station: Station): Station = { stations(station.id) = station; station }
  def getStation(id: String): Option[Station] = stations.get(id)
  def getAllStations: List[Station] = stations.values.toList
  def updateStation(station: Station): Unit = stations(station.id) = station

  // Sessions
  def addSession(session: ChargingSession): ChargingSession = { sessions(session.id) = session; session }
  def getSession(id: String): Option[ChargingSession] = sessions.get(id)
  def updateSession(session: ChargingSession): Unit = sessions(session.id) = session
  def getDriverSessions(driverId: String): List[ChargingSession] = sessions.values.filter(_.driverId == driverId).toList
  def getStationSessions(stationId: String): List[ChargingSession] = sessions.values.filter(_.stationId == stationId).toList

  // Promo codes
  def addPromoCode(promo: PromoCode): PromoCode = { promoCodes(promo.code) = promo; promo }
  def getPromoCode(code: String): Option[PromoCode] = promoCodes.get(code)
  def updatePromoCode(promo: PromoCode): Unit = promoCodes(promo.code) = promo
  def deletePromoCode(code: String): Boolean = promoCodes.remove(code).isDefined
}
