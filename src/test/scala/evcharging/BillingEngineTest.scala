package evcharging

import org.scalatest.funsuite.AnyFunSuite

class BillingEngineTest extends AnyFunSuite {

  test("DC tariff: 10 kWh at ₹20/kWh = ₹200") {
    val cost = BillingEngine.calculateCost(10.0, BillingEngine.dcTariff)
    assert(cost == 200.0, s"Expected ₹200, got ₹$cost")
  }

  test("DC tariff: 20 kWh (10@₹20 + 10@₹14) = ₹340") {
    val cost = BillingEngine.calculateCost(20.0, BillingEngine.dcTariff)
    assert(cost == 340.0, s"Expected ₹340, got ₹$cost")
  }

  test("DC tariff: 30 kWh (10@₹20 + 15@₹14 + 5@₹9) = ₹455") {
    val cost = BillingEngine.calculateCost(30.0, BillingEngine.dcTariff)
    assert(cost == 455.0, s"Expected ₹455, got ₹$cost")
  }

  test("DC minimum charge: 5 kWh = ₹150 (minimum)") {
    val cost = BillingEngine.calculateCost(5.0, BillingEngine.dcTariff)
    assert(cost == 150.0, s"Expected ₹150 minimum, got ₹$cost")
  }

  test("DC zero energy: 0 kWh = ₹150 (minimum)") {
    val cost = BillingEngine.calculateCost(0.0, BillingEngine.dcTariff)
    assert(cost == 150.0, s"Expected ₹150 minimum, got ₹$cost")
  }

  test("AC tariff: 5 kWh = ₹80 (5×₹15=₹75 is below the ₹80 minimum)") {
    val cost = BillingEngine.calculateCost(5.0, BillingEngine.acTariff)
    assert(cost == 80.0, s"Expected ₹80 minimum, got ₹$cost")
  }

  test("AC tariff: 10 kWh (5@₹15 + 5@₹12) = ₹135") {
    val cost = BillingEngine.calculateCost(10.0, BillingEngine.acTariff)
    assert(cost == 135.0, s"Expected ₹135, got ₹$cost")
  }

  test("AC tariff: 20 kWh (5@₹15 + 10@₹12 + 5@₹8) = ₹235") {
    val cost = BillingEngine.calculateCost(20.0, BillingEngine.acTariff)
    assert(cost == 235.0, s"Expected ₹235, got ₹$cost")
  }

  test("AC minimum charge: 3 kWh = ₹80 (minimum)") {
    val cost = BillingEngine.calculateCost(3.0, BillingEngine.acTariff)
    assert(cost == 80.0, s"Expected ₹80 minimum, got ₹$cost")
  }

  test("AC zero energy: 0 kWh = ₹80 (minimum)") {
    val cost = BillingEngine.calculateCost(0.0, BillingEngine.acTariff)
    assert(cost == 80.0, s"Expected ₹80 minimum, got ₹$cost")
  }

  test("Promo discount: 20% off ₹340 = ₹272") {
    val discounted = BillingEngine.applyDiscount(340.0, 20)
    assert(discounted == 272.0, s"Expected ₹272, got ₹$discounted")
  }

  test("Promo discount: 30% off ₹235 = ₹164.5") {
    val discounted = BillingEngine.applyDiscount(235.0, 30)
    assert(discounted == 164.5, s"Expected ₹164.5, got ₹$discounted")
  }

  test("AC-billed-on-DC case: 20 kWh on DC connector, billed at AC tariff") {
    val cost = BillingEngine.calculateCost(20.0, BillingEngine.acTariff)
    assert(cost == 235.0, s"Expected AC tariff ₹235, got ₹$cost")
  }
}
