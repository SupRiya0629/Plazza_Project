package evcharging

import cats.effect._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.typelevel.ci.CIString
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser.decode

// HTTP server: exposes all charging operations as REST APIs on port 8080.
object Server extends IOApp {
  private val storage = new Storage
  private val service = new ChargingService(storage)

  private val dsl = Http4sDsl[IO]
  import dsl._

  // Adds CORS headers so the browser frontend can call these APIs.
  private def addCorsHeaders(resp: Response[IO]): Response[IO] =
    resp.putHeaders(
      Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
      Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, DELETE, OPTIONS"),
      Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type")
    )

  // Parses the JSON body into type A, then runs f; returns 400 if the JSON is invalid.
  private def withJson[A: Decoder](req: Request[IO])(f: A => IO[Response[IO]]): IO[Response[IO]] =
    req.as[String].flatMap(body => decode[A](body).fold(_ => BadRequest("Invalid request"), f))

  // Converts an "AC"/"DC" string into the matching connector type.
  private def toConnectorType(s: String): ConnectorType = if (s == "AC") AC else DC

  // Turns a service result (Either) into an HTTP response.
  private def respond[A](result: Either[String, A])(ok: A => IO[Response[IO]]): IO[Response[IO]] =
    result.fold(BadRequest(_), ok)

  private val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // Register a driver.
    case req @ POST -> Root / "api" / "driver" / "register" =>
      withJson[DriverRequest](req) { r =>
        val d = storage.addDriver(Driver(name = r.name, email = r.email, phone = r.phone))
        Ok(DriverResponse(d.id, d.name, d.email, d.phone).asJson.toString)
      }

    // Register a vehicle for an existing driver.
    case req @ POST -> Root / "api" / "vehicle" / "register" =>
      withJson[VehicleRequest](req) { r =>
        if (storage.getDriver(r.driverId).isEmpty) BadRequest("Driver not found")
        else {
          val v = storage.addVehicle(Vehicle(driverId = r.driverId, make = r.make, model = r.model, regNumber = r.regNumber))
          Ok(VehicleResponse(v.id, v.driverId, v.make, v.model, v.regNumber).asJson.toString)
        }
      }

    // Register a charging station at a location.
    case req @ POST -> Root / "api" / "station" / "register" =>
      withJson[StationRequest](req) { r =>
        val s = storage.addStation(Station(name = r.name, latitude = r.latitude, longitude = r.longitude))
        Ok(StationResponse(s.id, s.name, s.latitude, s.longitude).asJson.toString)
      }

    // Add a connector (AC or DC) to a station.
    case req @ POST -> Root / "api" / "connector" / "add" =>
      withJson[ConnectorRequest](req) { r =>
        respond(service.addConnector(r.stationId, toConnectorType(r.connectorType), r.power)) { c =>
          Ok(ConnectorResponse(c.id, c.stationId, c.connectorType.name, c.power).asJson.toString)
        }
      }

    // List every connector at a station with its live status.
    case GET -> Root / "api" / "station" / stationId / "connectors" =>
      respond(service.listConnectors(stationId)) { list =>
        Ok(list.map(c => ConnectorInfo(c.id, c.connectorType, c.power, c.inService, c.free)).asJson.toString)
      }

    // Take a connector out of service.
    case POST -> Root / "api" / "connector" / connectorId / "deactivate" =>
      respond(service.deactivateConnector(connectorId))(_ => Ok("""{"status":"deactivated"}"""))

    // Bring a connector back into service.
    case POST -> Root / "api" / "connector" / connectorId / "activate" =>
      respond(service.activateConnector(connectorId))(_ => Ok("""{"status":"activated"}"""))

    // Start a charging session (auto-picks a free connector of the requested type).
    case req @ POST -> Root / "api" / "session" / "start" =>
      withJson[StartSessionRequest](req) { r =>
        respond(service.startChargingSession(r.driverId, r.stationId, toConnectorType(r.connectorType), r.promoCode)) { s =>
          Ok(StartSessionResponse(s.id, s.baseCost.getOrElse(0), s.discount.getOrElse(0), s.finalCost.getOrElse(0)).asJson.toString)
        }
      }

    // End a session with the energy delivered and get the final bill.
    case req @ POST -> Root / "api" / "session" / "end" =>
      withJson[EndSessionRequest](req) { r =>
        respond(service.endChargingSession(r.sessionId, r.energyDelivered)) { s =>
          Ok(EndSessionResponse(s.id, s.finalCost.getOrElse(0)).asJson.toString)
        }
      }

    // Find stations within a radius of a location.
    case req @ POST -> Root / "api" / "nearby-stations" =>
      withJson[NearbyRequest](req) { r =>
        val stations = service.findNearbyStations(r.latitude, r.longitude, r.radiusKm)
        Ok(stations.map(s => NearbyStationResponse(s.id, s.name, s.latitude, s.longitude, 0.0)).asJson.toString)
      }

    // Create a promo code.
    case req @ POST -> Root / "api" / "promo" / "create" =>
      withJson[PromoRequest](req) { r =>
        respond(service.createPromoCode(r.code, r.discountPercent, r.maxUses)) { p =>
          Ok(PromoResponse(p.code, p.discountPercent).asJson.toString)
        }
      }

    // Delete a promo code.
    case DELETE -> Root / "api" / "promo" / code =>
      IO(storage.deletePromoCode(code)) *> Ok("Promo code deleted")

    // Session history for a driver.
    case GET -> Root / "api" / "history" / "driver" / driverId =>
      val sessions = storage.getDriverSessions(driverId)
      Ok(sessions.map(s => HistoryResponse(s.id, s.stationId, s.energyDelivered.getOrElse(0), s.finalCost.getOrElse(0))).asJson.toString)

    // Session history for a station.
    case GET -> Root / "api" / "history" / "station" / stationId =>
      val sessions = storage.getStationSessions(stationId)
      Ok(sessions.map(s => HistoryResponse(s.id, s.driverId, s.energyDelivered.getOrElse(0), s.finalCost.getOrElse(0))).asJson.toString)

    // Browser preflight requests.
    case OPTIONS -> _ => Ok()
  }

  // Starts the server.
  def run(args: List[String]): IO[ExitCode] = {
    println("\n⚡ EV Charging Network Server\n📍 Running on: http://0.0.0.0:8080\n")
    BlazeServerBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(routes.map(addCorsHeaders).orNotFound)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }
}

// ---- Request/response shapes (plain data, serialized to/from JSON) ----

case class DriverRequest(name: String, email: String, phone: String)
case class DriverResponse(id: String, name: String, email: String, phone: String)

case class VehicleRequest(driverId: String, make: String, model: String, regNumber: String)
case class VehicleResponse(id: String, driverId: String, make: String, model: String, regNumber: String)

case class StationRequest(name: String, latitude: Double, longitude: Double)
case class StationResponse(id: String, name: String, latitude: Double, longitude: Double)

case class ConnectorRequest(stationId: String, connectorType: String, power: Double)
case class ConnectorResponse(id: String, stationId: String, connectorType: String, power: Double)
case class ConnectorInfo(id: String, connectorType: String, power: Double, inService: Boolean, free: Boolean)

case class StartSessionRequest(driverId: String, stationId: String, connectorType: String, promoCode: Option[String] = None)
case class StartSessionResponse(sessionId: String, baseCost: Double, discount: Double, finalCost: Double)

case class EndSessionRequest(sessionId: String, energyDelivered: Double)
case class EndSessionResponse(sessionId: String, finalCost: Double)

case class NearbyRequest(latitude: Double, longitude: Double, radiusKm: Double)
case class NearbyStationResponse(id: String, name: String, latitude: Double, longitude: Double, distance: Double)

case class PromoRequest(code: String, discountPercent: Double, maxUses: Option[Int] = None)
case class PromoResponse(code: String, discountPercent: Double)

case class HistoryResponse(sessionId: String, stationOrDriverId: String, energy: Double, cost: Double)
