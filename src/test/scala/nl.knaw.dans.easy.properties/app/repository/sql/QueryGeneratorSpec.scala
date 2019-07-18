package nl.knaw.dans.easy.properties.app.repository.sql

import java.util.UUID

import cats.data.NonEmptyList
import nl.knaw.dans.easy.properties.app.model.SeriesFilter
import nl.knaw.dans.easy.properties.app.model.ingestStep.{ DepositIngestStepFilter, IngestStepLabel }
import nl.knaw.dans.easy.properties.app.model.state.{ DepositStateFilter, StateLabel }
import nl.knaw.dans.easy.properties.app.repository.DepositFilters
import nl.knaw.dans.easy.properties.fixture.TestSupportFixture
import org.scalactic.{ AbstractStringUniformity, Uniformity }

class QueryGeneratorSpec extends TestSupportFixture {

  val whiteSpaceNormalised: Uniformity[String] = {
    new AbstractStringUniformity {
      def normalized(s: String): String = {
        s.replace("\n", " ")
          .replaceAll("\\s\\s+", " ")
          .replaceAll("\\( ", "(")
          .replaceAll(" \\)", ")")
      }

      override def toString: String = "whiteSpaceNormalised"
    }
  }

  "getAllDeposits" should "query for all deposits" in {
    QueryGenerator.getAllDeposits shouldBe "SELECT * FROM Deposit;"
  }

  "findDeposits" should "render the depositIds as comma separated question marks" in {
    val depositIds = NonEmptyList.fromListUnsafe((1 to 5).map(_ => UUID.randomUUID()).toList)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |WHERE depositId IN (?, ?, ?, ?, ?);""".stripMargin

    QueryGenerator.findDeposits(depositIds) should equal(expectedQuery)(after being whiteSpaceNormalised)
  }

  "searchDeposits" should "render a query that selects all deposits when no filters are set" in {
    val filter = DepositFilters()
    val (query, values) = QueryGenerator.searchDeposits(filter)

    query shouldBe "SELECT * FROM Deposit;"
    values shouldBe empty
  }

  it should "render a query that searches for deposits of a certain depositor" in {
    val filter = DepositFilters(depositorId = Some("user001"))
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |WHERE depositorId = ?;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain only "user001"
  }

  it should "render a query that searches for deposits with a null bag name" in {
    val filter = DepositFilters(bagName = Some(null))
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |WHERE bagName IS NULL;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values shouldBe empty
  }

  it should "render a query that searches for deposits with a certain bag name" in {
    val filter = DepositFilters(bagName = Some("my-bag"))
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |WHERE bagName = ?;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain only "my-bag"
  }

  it should "render a query that searches for deposits of a certain depositor and with a certain bag name" in {
    val filter = DepositFilters(
      depositorId = Some("user001"),
      bagName = Some("my-bag"),
    )
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |WHERE depositorId = ?
        |AND bagName = ?;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain inOrderOnly("user001", "my-bag")
  }

  it should "render a query that searches for deposits with a certain 'latest state'" in {
    val filter = DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.ARCHIVED, SeriesFilter.LATEST)))
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |INNER JOIN (
        |  SELECT State.depositId
        |  FROM State
        |  INNER JOIN (
        |    SELECT depositId, max(timestamp) AS max_timestamp
        |    FROM State
        |    GROUP BY depositId
        |  ) AS StateWithMaxTimestamp
        |  ON State.timestamp = StateWithMaxTimestamp.max_timestamp
        |  WHERE label = ?
        |) AS StateSearchResult
        |ON Deposit.depositId = StateSearchResult.depositId;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain only StateLabel.ARCHIVED.toString
  }

  it should "render a query that searches for deposits that at some time had this certain state label" in {
    val filter = DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.SUBMITTED, SeriesFilter.ALL)))
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |INNER JOIN (
        |  SELECT DISTINCT depositId
        |  FROM State
        |  WHERE label = ?
        |) AS StateSearchResult ON Deposit.depositId = StateSearchResult.depositId;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain only StateLabel.SUBMITTED.toString
  }

  it should "render a query that searches for deposits of a certain depositor, with a certain bagName and with a certain 'latest state'" in {
    val filter = DepositFilters(
      depositorId = Some("user001"),
      bagName = Some("my-bag"),
      stateFilter = Some(DepositStateFilter(StateLabel.SUBMITTED, SeriesFilter.LATEST)),
    )
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM (
        |  SELECT *
        |  FROM Deposit
        |  WHERE depositorId = ?
        |  AND bagName = ?
        |) AS SelectedDeposits
        |INNER JOIN (
        |  SELECT State.depositId
        |  FROM State
        |  INNER JOIN (
        |    SELECT depositId, max(timestamp) AS max_timestamp
        |    FROM State
        |    GROUP BY depositId
        |  ) AS StateWithMaxTimestamp
        |  ON State.timestamp = StateWithMaxTimestamp.max_timestamp
        |  WHERE label = ?
        |) AS StateSearchResult
        |ON SelectedDeposits.depositId = StateSearchResult.depositId;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain inOrderOnly(
      "user001",
      "my-bag",
      StateLabel.SUBMITTED.toString
    )
  }

  it should "render a query that searches for deposits with a certain 'latest ingest step'" in {
    val filter = DepositFilters(ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.FEDORA, SeriesFilter.LATEST)))
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |INNER JOIN (
        |  SELECT SimpleProperties.depositId
        |  FROM SimpleProperties
        |  INNER JOIN (
        |    SELECT depositId, max(timestamp) AS max_timestamp
        |    FROM SimpleProperties
        |    WHERE key = ?
        |    GROUP BY depositId
        |  ) AS SimplePropertiesWithMaxTimestamp
        |  ON SimpleProperties.timestamp = SimplePropertiesWithMaxTimestamp.max_timestamp
        |  WHERE value = ?
        |) AS SimplePropertiesSearchResult
        |ON Deposit.depositId = SimplePropertiesSearchResult.depositId;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain inOrderOnly("ingest-step", IngestStepLabel.FEDORA.toString)
  }

  it should "render a query that searches for deposits that sometime had this certain ingest step" in {
    val filter = DepositFilters(ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.FEDORA, SeriesFilter.ALL)))
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |INNER JOIN (
        |  SELECT DISTINCT depositId
        |  FROM SimpleProperties
        |  WHERE key = ?
        |  AND value = ?
        |) AS SimplePropertiesSearchResult ON Deposit.depositId = SimplePropertiesSearchResult.depositId;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain inOrderOnly("ingest-step", IngestStepLabel.FEDORA.toString)
  }

  it should "render a query that searches for deposits with a certain 'latest state' and that sometime had this certain ingest step" in {
    val filter = DepositFilters(
      stateFilter = Some(DepositStateFilter(StateLabel.SUBMITTED, SeriesFilter.LATEST)),
      ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.FEDORA, SeriesFilter.ALL)),
    )
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM Deposit
        |INNER JOIN (
        |  SELECT State.depositId
        |  FROM State
        |  INNER JOIN (
        |    SELECT depositId, max(timestamp) AS max_timestamp
        |    FROM State
        |    GROUP BY depositId
        |  ) AS StateWithMaxTimestamp
        |  ON State.timestamp = StateWithMaxTimestamp.max_timestamp
        |  WHERE label = ?
        |) AS StateSearchResult
        |ON Deposit.depositId = StateSearchResult.depositId
        |INNER JOIN (
        |  SELECT DISTINCT depositId
        |  FROM SimpleProperties
        |  WHERE key = ?
        |  AND value = ?
        |) AS SimplePropertiesSearchResult ON Deposit.depositId = SimplePropertiesSearchResult.depositId;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain inOrderOnly(StateLabel.SUBMITTED.toString, "ingest-step", IngestStepLabel.FEDORA.toString)
  }

  it should "render a query that searches for deposits of a certain depositor, with a certain bagName, with a certain 'latest state' and that sometime had this certain ingest step" in {
    val filter = DepositFilters(
      depositorId = Some("user001"),
      bagName = Some("my-bag"),
      stateFilter = Some(DepositStateFilter(StateLabel.SUBMITTED, SeriesFilter.LATEST)),
      ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.FEDORA, SeriesFilter.ALL)),
    )
    val (query, values) = QueryGenerator.searchDeposits(filter)

    val expectedQuery =
      """SELECT *
        |FROM (
        |  SELECT *
        |  FROM Deposit
        |  WHERE depositorId = ?
        |  AND bagName = ?
        |) AS SelectedDeposits
        |INNER JOIN (
        |  SELECT State.depositId
        |  FROM State
        |  INNER JOIN (
        |    SELECT depositId, max(timestamp) AS max_timestamp
        |    FROM State
        |    GROUP BY depositId
        |  ) AS StateWithMaxTimestamp
        |  ON State.timestamp = StateWithMaxTimestamp.max_timestamp
        |  WHERE label = ?
        |) AS StateSearchResult
        |ON SelectedDeposits.depositId = StateSearchResult.depositId
        |INNER JOIN (
        |  SELECT DISTINCT depositId
        |  FROM SimpleProperties
        |  WHERE key = ?
        |  AND value = ?
        |) AS SimplePropertiesSearchResult ON SelectedDeposits.depositId = SimplePropertiesSearchResult.depositId;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values should contain inOrderOnly("user001", "my-bag", StateLabel.SUBMITTED.toString, "ingest-step", IngestStepLabel.FEDORA.toString)
  }

  "storeDeposit" should "yield the query for inserting a deposit to the database" in {
    QueryGenerator.storeDeposit() shouldBe "INSERT INTO Deposit (depositId, bagName, creationTimestamp, depositorId) VALUES (?, ?, ?, ?);"
  }

  "getLastModifiedDate" should "generate a UNION query" in {
    val depositIds = NonEmptyList.fromListUnsafe((1 to 5).map(_ => UUID.randomUUID()).toList)
    val (query, values) = QueryGenerator.getLastModifiedDate(depositIds)

    val expectedQuery =
      s"""SELECT depositId, MAX(max) AS max_timestamp
         |FROM (
         |  (SELECT depositId, MAX(creationTimestamp) AS max FROM Deposit WHERE depositId IN (?, ?, ?, ?, ?) GROUP BY depositId) UNION ALL
         |  (SELECT depositId, MAX(timestamp) AS max FROM State WHERE depositId IN (?, ?, ?, ?, ?) GROUP BY depositId) UNION ALL
         |  (SELECT depositId, MAX(timestamp) AS max FROM Identifier WHERE depositId IN (?, ?, ?, ?, ?) GROUP BY depositId) UNION ALL
         |  (SELECT depositId, MAX(timestamp) AS max FROM Curation WHERE depositId IN (?, ?, ?, ?, ?) GROUP BY depositId) UNION ALL
         |  (SELECT depositId, MAX(timestamp) AS max FROM Springfield WHERE depositId IN (?, ?, ?, ?, ?) GROUP BY depositId) UNION ALL
         |  (SELECT depositId, MAX(timestamp) AS max FROM SimpleProperties WHERE depositId IN (?, ?, ?, ?, ?) GROUP BY depositId)
         |) AS max_timestamps
         |GROUP BY depositId;""".stripMargin

    query should equal(expectedQuery)(after being whiteSpaceNormalised)
    values.toList should {
      have length (depositIds.size * 6) and
        contain theSameElementsAs Seq.fill(6)(depositIds.map(_.toString).toList).flatten
    }
  }
}
