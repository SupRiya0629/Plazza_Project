package evcharging

// Pure pricing logic: no state, so it is easy to test and reuse.
object BillingEngine {

  // DC (fast): min ₹150; first 10 kWh @₹20; next 15 @₹14; rest @₹9.
  val dcTariff = Tariff(DC, 150.0, List(
    TariffSlab(0.0, Some(10.0), 20.0),
    TariffSlab(10.0, Some(25.0), 14.0),
    TariffSlab(25.0, None, 9.0)
  ))

  // AC (slow): min ₹80; first 5 kWh @₹15; next 10 @₹12; rest @₹8.
  val acTariff = Tariff(AC, 80.0, List(
    TariffSlab(0.0, Some(5.0), 15.0),
    TariffSlab(5.0, Some(15.0), 12.0),
    TariffSlab(15.0, None, 8.0)
  ))

  // Pick the tariff for a connector type.
  def getTariff(connectorType: ConnectorType): Tariff = connectorType match {
    case DC => dcTariff
    case AC => acTariff
  }

  // Cost for the energy delivered, charging each slab at its rate, never below the minimum.
  def calculateCost(energyKwh: Double, tariff: Tariff): Double = {
    var cost = 0.0
    for (slab <- tariff.slabs) {
      // How much of the energy falls inside this slab's range.
      val slabTop = slab.kWhEnd.getOrElse(Double.MaxValue)
      val inSlab = Math.min(energyKwh, slabTop) - slab.kWhStart
      if (inSlab > 0) cost += inSlab * slab.ratePerKWh
    }
    Math.max(cost, tariff.minimumCharge)
  }

  // Apply a percentage discount.
  def applyDiscount(cost: Double, discountPercent: Double): Double = cost * (1.0 - discountPercent / 100.0)
}
