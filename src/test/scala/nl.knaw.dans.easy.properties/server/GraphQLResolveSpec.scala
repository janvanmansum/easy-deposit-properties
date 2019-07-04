/**
 * Copyright (C) 2019 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.properties.server

import java.util.UUID

import better.files.File
import cats.syntax.either._
import nl.knaw.dans.easy.properties.app.model.contentType.{ ContentType, ContentTypeValue, DepositContentTypeFilter }
import nl.knaw.dans.easy.properties.app.model.curation.Curation
import nl.knaw.dans.easy.properties.app.model.curator.DepositCuratorFilter
import nl.knaw.dans.easy.properties.app.model.identifier.IdentifierType.IdentifierType
import nl.knaw.dans.easy.properties.app.model.identifier.{ Identifier, IdentifierType }
import nl.knaw.dans.easy.properties.app.model.ingestStep.{ DepositIngestStepFilter, IngestStep, IngestStepLabel }
import nl.knaw.dans.easy.properties.app.model.springfield.{ Springfield, SpringfieldPlayMode }
import nl.knaw.dans.easy.properties.app.model.state.{ DepositStateFilter, State, StateLabel }
import nl.knaw.dans.easy.properties.app.model.{ Deposit, DepositCurationPerformedFilter, DepositCurationRequiredFilter, DepositDoiActionFilter, DepositDoiRegisteredFilter, DepositId, DepositIsNewVersionFilter, DoiAction, DoiActionEvent, DoiRegisteredEvent, SeriesFilter }
import nl.knaw.dans.easy.properties.app.repository.{ ContentTypeDao, CurationDao, DepositDao, DepositFilters, DoiActionDao, DoiRegisteredDao, IdentifierDao, IngestStepDao, Repository, SpringfieldDao, StateDao }
import nl.knaw.dans.easy.properties.fixture.{ FileSystemSupport, TestSupportFixture }
import org.joda.time.DateTime
import org.json4s.JsonAST.JNothing
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._
import org.json4s.{ DefaultFormats, Formats }
import org.scalamock.scalatest.MockFactory
import org.scalatra.test.EmbeddedJettyContainer
import org.scalatra.test.scalatest.ScalatraSuite

import scala.language.implicitConversions

trait GraphQLResolveSpecTestObjects {
  val depositId1: DepositId = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val depositId2: DepositId = UUID.fromString("00000000-0000-0000-0000-000000000002")
  val depositId3: DepositId = UUID.fromString("00000000-0000-0000-0000-000000000003")
  val deposit1 = Deposit(
    id = depositId1,
    bagName = Some("bag1"),
    creationTimestamp = DateTime.now(),
    depositorId = "user001",
  )
  val deposit2 = Deposit(
    id = depositId2,
    bagName = Some("bag2"),
    creationTimestamp = DateTime.now(),
    depositorId = "user002",
  )
  val deposit3 = Deposit(
    id = depositId3,
    bagName = Some("bag3"),
    creationTimestamp = DateTime.now(),
    depositorId = "user002",
  )
  val state1 = State(
    id = "1",
    label = StateLabel.ARCHIVED,
    description = "your deposit is submitted",
    timestamp = DateTime.now(),
  )
  val state2 = State(
    id = "2",
    label = StateLabel.DRAFT,
    description = "your deposit is in draft",
    timestamp = DateTime.now(),
  )
  val state3 = State(
    id = "3",
    label = StateLabel.ARCHIVED,
    description = "your deposit is submitted",
    timestamp = DateTime.now(),
  )
  val step1 = IngestStep(
    id = "1",
    step = IngestStepLabel.VALIDATE,
    timestamp = DateTime.now(),
  )
  val step2 = IngestStep(
    id = "2",
    step = IngestStepLabel.FEDORA,
    timestamp = DateTime.now(),
  )
  val step3 = IngestStep(
    id = "3",
    step = IngestStepLabel.COMPLETED,
    timestamp = DateTime.now(),
  )
  val identifier1 = Identifier(
    id = "1",
    idType = IdentifierType.URN,
    idValue = "abcdef",
    timestamp = DateTime.now(),
  )
  val identifier2 = Identifier(
    id = "2",
    idType = IdentifierType.DOI,
    idValue = "123456",
    timestamp = DateTime.now(),
  )
  val identifier3 = Identifier(
    id = "3",
    idType = IdentifierType.FEDORA,
    idValue = "easy-dataset:1",
    timestamp = DateTime.now(),
  )
  val doiRegisteredEvent1 = DoiRegisteredEvent(
    value = true,
    timestamp = DateTime.now(),
  )
  val doiRegisteredEvent2 = DoiRegisteredEvent(
    value = false,
    timestamp = DateTime.now(),
  )
  val doiActionEvent1 = DoiActionEvent(
    value = DoiAction.CREATE,
    timestamp = DateTime.now(),
  )
  val doiActionEvent2 = DoiActionEvent(
    value = DoiAction.UPDATE,
    timestamp = DateTime.now(),
  )
  val curation1 = Curation(
    id = "1",
    isNewVersion = true,
    isRequired = true,
    isPerformed = true,
    datamanagerUserId = "archie001",
    datamanagerEmail = "does-not-exists1@dans.knaw.nl",
    timestamp = DateTime.now(),
  )
  val curation2 = Curation(
    id = "2",
    isNewVersion = false,
    isRequired = false,
    isPerformed = false,
    datamanagerUserId = "archie002",
    datamanagerEmail = "does-not-exists2@dans.knaw.nl",
    timestamp = DateTime.now(),
  )
  val springfield1 = Springfield(
    id = "1",
    domain = "domain1",
    user = "user1",
    collection = "collection1",
    playmode = SpringfieldPlayMode.CONTINUOUS,
    timestamp = DateTime.now(),
  )
  val springfield2 = Springfield(
    id = "2",
    domain = "domain2",
    user = "user2",
    collection = "collection2",
    playmode = SpringfieldPlayMode.MENU,
    timestamp = DateTime.now(),
  )
  val contentType1 = ContentType(
    id = "1",
    value = ContentTypeValue.ZIP,
    timestamp = DateTime.now(),
  )
  val contentType2 = ContentType(
    id = "2",
    value = ContentTypeValue.OCTET,
    timestamp = DateTime.now(),
  )
}

class GraphQLResolveSpec extends TestSupportFixture
  with MockFactory
  with FileSystemSupport
  with GraphQLResolveSpecTestObjects
  with EmbeddedJettyContainer
  with ScalatraSuite {

  private val graphqlExamplesDir = testDir / "graphql"
  private val depositDao = mock[DepositDao]
  private val stateDao = mock[StateDao]
  private val ingestStepDao = mock[IngestStepDao]
  private val identifierDao = mock[IdentifierDao]
  private val doiRegisteredDao = mock[DoiRegisteredDao]
  private val doiActionDao = mock[DoiActionDao]
  private val curationDao = mock[CurationDao]
  private val springfieldDao = mock[SpringfieldDao]
  private val contentTypeDao = mock[ContentTypeDao]
  private val repository = Repository(depositDao, stateDao, ingestStepDao, identifierDao, doiRegisteredDao, doiActionDao, curationDao, springfieldDao, contentTypeDao)
  private val servlet = DepositPropertiesGraphQLServlet(() => repository)
  private implicit val jsonFormats: Formats = new DefaultFormats {}

  addServlet(servlet, "/*")

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    File(getClass.getResource("/graphql-examples"))
      .copyTo(graphqlExamplesDir)
  }

  "graphql" should "resolve 'deposit/findDeposit/plain.graphql' with 19 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "findDeposit" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      inAnyOrder {
        depositDao.lastModified _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Some(DateTime.now()),
        ).asRight
        stateDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Some(state1),
        ).asRight
        ingestStepDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Some(step1),
        ).asRight
        identifierDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(identifier1, identifier2),
        ).asRight
        doiRegisteredDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Some(doiRegisteredEvent1),
        ).asRight
        doiRegisteredDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(doiRegisteredEvent1, doiRegisteredEvent2),
        ).asRight
        doiActionDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Some(doiActionEvent1),
        ).asRight
        doiActionDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(doiActionEvent1, doiActionEvent2),
        ).asRight
        curationDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Some(curation1),
        ).asRight
        curationDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(curation1, curation2),
        ).asRight
        springfieldDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Some(springfield1),
        ).asRight
        springfieldDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(springfield1, springfield2),
        ).asRight
        contentTypeDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Some(contentType1),
        ).asRight
        contentTypeDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(contentType1, contentType2),
        ).asRight
      }
    }

    runQuery(input)
  }

  it should "resolve 'deposit/findIdentifierWithType/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "findIdentifierWithType" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      (identifierDao.getByType(_: Seq[(DepositId, IdentifierType)])) expects Seq((depositId1, IdentifierType.DOI), (depositId1, IdentifierType.BAG_STORE)) once() returning Seq(
        (depositId1, IdentifierType.DOI) -> Some(identifier2),
        (depositId1, IdentifierType.BAG_STORE) -> Some(identifier1),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/ingestStepPagination/firstPage.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "ingestStepPagination" / "firstPage.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      ingestStepDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(step1, step2, step3, step1, step2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/ingestStepPagination/secondPage.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "ingestStepPagination" / "secondPage.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      ingestStepDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(step1, step2, step3, step1, step2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/ingestStepPagination/thirdPage.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "ingestStepPagination" / "thirdPage.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      ingestStepDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(step1, step2, step3, step1, step2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listAllContentTypesOfDeposit/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listAllContentTypesOfDeposit" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      contentTypeDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(contentType2, contentType1),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listAllCuratorsOfDeposit/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listAllCuratorsOfDeposit" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      curationDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(curation1, curation2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listAllIngestStepsOfDeposit/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listAllIngestStepsOfDeposit" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      ingestStepDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(step1, step2, step3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listAllSpringfieldsOfDeposit/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listAllSpringfieldsOfDeposit" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId2 once() returning deposit2.asRight
      springfieldDao.getAll _ expects Seq(depositId2) once() returning Seq(
        depositId2 -> Seq(springfield1, springfield2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listAllStatesOfDeposit/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listAllStatesOfDeposit" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      stateDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(state1, state2, state3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listDepositsWithContentTypeFilterAll/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listDepositsWithContentTypeFilterAll" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId2 once() returning deposit2.asRight
      contentTypeDao.getCurrent _ expects Seq(depositId2) once() returning Seq(
        depositId2 -> Some(contentType2),
      ).asRight
      val filters = DepositFilters(contentTypeFilter = Some(DepositContentTypeFilter(ContentTypeValue.OCTET, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit2, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listDepositsWithCuratorFilterAll/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listDepositsWithCuratorFilterAll" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      curationDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(curation1),
      ).asRight
      val filters = DepositFilters(curatorFilter = Some(DepositCuratorFilter("archie001", SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listDepositsWithIngestStepFilterAll/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listDepositsWithIngestStepFilterAll" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      ingestStepDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Some(step1)
      ).asRight
      val filters = DepositFilters(ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.VALIDATE, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listDepositsWithSameContentType/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listDepositsWithSameContentType" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId2 once() returning deposit2.asRight
      contentTypeDao.getCurrent _ expects Seq(depositId2) once() returning Seq(
        depositId2 -> Some(contentType2),
      ).asRight
      val filters = DepositFilters(contentTypeFilter = Some(DepositContentTypeFilter(ContentTypeValue.OCTET)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit3, deposit2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listDepositsWithSameCurator/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listDepositsWithSameCurator" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      curationDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(curation2),
      ).asRight
      val filters = DepositFilters(curatorFilter = Some(DepositCuratorFilter("archie002")))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listDepositsWithSameDepositor/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listDepositsWithSameDepositor" / "plain.graphql"
    assume(input.exists, s"input file does not exist: $input")
    val query = input.contentAsString.replace(depositId1.toString, depositId2.toString)

    inSequence {
      depositDao.find _ expects depositId2 once() returning deposit2.asRight
      val filters = DepositFilters(depositorId = Some("user002"))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit2, deposit3),
      ).asRight
      (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId2, depositId3) once() returning Seq(
        depositId2 -> Some(state2),
        depositId3 -> Some(state3),
      ).asRight
    }

    runQuery(query)
  }

  it should "resolve 'deposit/listDepositsWithSameIngestStep/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listDepositsWithSameIngestStep" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      ingestStepDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Some(step1)
      ).asRight
      val filters = DepositFilters(ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.VALIDATE, SeriesFilter.LATEST)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listDepositsWithSameState/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listDepositsWithSameState" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      stateDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Some(state1),
      ).asRight
      val filters = DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.ARCHIVED, SeriesFilter.LATEST)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/listDepositsWithStateFilterAll/plain.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "listDepositsWithStateFilterAll" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      stateDao.getCurrent _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Some(state1),
      ).asRight
      val filters = DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.ARCHIVED, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/statePagination/firstPage.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "statePagination" / "firstPage.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      stateDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(state1, state2, state3, state1, state2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/statePagination/secondPage.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "statePagination" / "secondPage.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      stateDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(state1, state2, state3, state1, state2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/statePagination/thirdPage.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "statePagination" / "thirdPage.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      stateDao.getAll _ expects Seq(depositId1) once() returning Seq(
        depositId1 -> Seq(state1, state2, state3, state1, state2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposit/timebasedSearch/plain.graphql' with 6 calls to the repository" in {
    val input = graphqlExamplesDir / "deposit" / "timebasedSearch" / "plain.graphql"

    inSequence {
      depositDao.find _ expects depositId1 once() returning deposit1.asRight
      inAnyOrder {
        stateDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(state1, state2, state3, state1, state2),
        ).asRight
        ingestStepDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(step1, step2, step3, step1, step2),
        ).asRight
        curationDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(curation1, curation2, curation1),
        ).asRight
        springfieldDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(springfield1, springfield2, springfield1),
        ).asRight
        contentTypeDao.getAll _ expects Seq(depositId1) once() returning Seq(
          depositId1 -> Seq(contentType1, contentType2, contentType1),
        ).asRight
      }
    }

    runQuery(input)
  }

  // Currently 'once()' fails, because it is 'repeated 4'.
  // However, once we got caching working, we should see 'once()' working properly.
  it should "resolve 'deposit/nested.graphql' with n calls to the repository" in pendingUntilFixed {
    val input = graphqlExamplesDir / "deposit" / "nested.graphql"

    val filters = DepositFilters()
    depositDao.search _ expects Seq(filters) once() returning Seq(
      filters -> Seq(deposit1, deposit2, deposit3),
    ).asRight
    (stateDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
      depositId1 -> Seq(state1, state2),
      depositId2 -> Seq(state2, state3),
      depositId3 -> Seq.empty,
    ).asRight
    depositDao.search _ expects Seq(
      DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.ARCHIVED))),
      DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.DRAFT))),
    ) once() returning Seq(
      DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.ARCHIVED))) -> Seq(deposit1, deposit2, deposit3),
      DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.DRAFT))) -> Seq(deposit1, deposit2, deposit3),
    ).asRight

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithContentTypeAndDepositor/plain.graphql' with 1 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithContentTypeAndDepositor" / "plain.graphql"

    val filters = DepositFilters(depositorId = Some("user001"), contentTypeFilter = Some(DepositContentTypeFilter(ContentTypeValue.ZIP)))
    depositDao.search _ expects Seq(filters) once() returning Seq(
      filters -> Seq(deposit2, deposit3),
    ).asRight

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithCurationPerformedAndDepositor/plain.graphql' with 1 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithCurationPerformedAndDepositor" / "plain.graphql"

    val filters = DepositFilters(depositorId = Some("user001"), curationPerformedFilter = Some(DepositCurationPerformedFilter(curationPerformed = true)))
    depositDao.search _ expects Seq(filters) once() returning Seq(
      filters -> Seq(deposit2, deposit3),
    ).asRight

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithCurationRequiredAndDepositor/plain.graphql' with 1 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithCurationRequiredAndDepositor" / "plain.graphql"

    val filters = DepositFilters(depositorId = Some("user001"), curationRequiredFilter = Some(DepositCurationRequiredFilter(curationRequired = true)))
    depositDao.search _ expects Seq(filters) once() returning Seq(
      filters -> Seq(deposit2, deposit3),
    ).asRight

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithCuratorAndDepositor/plain.graphql' with 1 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithCuratorAndDepositor" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(depositorId = Some("user001"), curatorFilter = Some(DepositCuratorFilter("archie001")))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit2, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithDepositor/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithDepositor" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(depositorId = Some("user002"))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit2, deposit3),
      ).asRight
      (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId2, depositId3) once() returning Seq(
        depositId2 -> Some(state2),
        depositId3 -> Some(state3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithDoiActionAndDepositor/plain.graphql' with 1 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithDoiActionAndDepositor" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(depositorId = Some("user001"), doiActionFilter = Some(DepositDoiActionFilter(DoiAction.CREATE)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit2, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithDoiRegisteredAndDepositor/plain.graphql' with 1 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithDoiRegisteredAndDepositor" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(depositorId = Some("user001"), doiRegisteredFilter = Some(DepositDoiRegisteredFilter(value = true)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit2, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithIngestStepAndDepositor/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithIngestStepAndDepositor" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(depositorId = Some("user001"), ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.COMPLETED, SeriesFilter.LATEST)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit2, deposit3),
      ).asRight
      (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId2, depositId3) once() returning Seq(
        depositId2 -> Some(state2),
        depositId3 -> Some(state3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithIsNewVersionAndDepositor/plain.graphql' with 1 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithIsNewVersionAndDepositor" / "plain.graphql"

    val filters = DepositFilters(depositorId = Some("user001"), isNewVersionFilter = Some(DepositIsNewVersionFilter(isNewVersion = false)))
    depositDao.search _ expects Seq(filters) once() returning Seq(
      filters -> Seq(deposit2, deposit3),
    ).asRight

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithStateAndDepositor/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithStateAndDepositor" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(depositorId = Some("user001"), stateFilter = Some(DepositStateFilter(StateLabel.ARCHIVED, SeriesFilter.LATEST)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
      (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId3) once() returning Seq(
        depositId1 -> Some(state1),
        depositId3 -> Some(state3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'depositor/listDepositsWithStateFilterAllAndDepositor/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "depositor" / "listDepositsWithStateFilterAllAndDepositor" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(depositorId = Some("user001"), stateFilter = Some(DepositStateFilter(StateLabel.DRAFT, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
      (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId3) once() returning Seq(
        depositId1 -> Some(state1),
        depositId3 -> Some(state3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listAllDeposits/plain.graphql' with 19 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listAllDeposits" / "plain.graphql"

    inSequence {
      val filters = DepositFilters()
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      inAnyOrder {
        (depositDao.lastModified(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Some(DateTime.now()),
          depositId2 -> Some(DateTime.now()),
          depositId3 -> Some(DateTime.now()),
        ).asRight
        (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Some(state1),
          depositId2 -> Some(state2),
          depositId3 -> Some(state3),
        ).asRight
        (ingestStepDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Some(step1),
          depositId2 -> Some(step2),
          depositId3 -> Some(step3),
        ).asRight
        (identifierDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Seq(identifier1, identifier2),
          depositId2 -> Seq(identifier2, identifier3),
          depositId3 -> Seq.empty,
        ).asRight
        (doiRegisteredDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Some(doiRegisteredEvent1),
          depositId2 -> Some(doiRegisteredEvent2),
          depositId3 -> None,
        ).asRight
        (doiRegisteredDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Seq(doiRegisteredEvent1, doiRegisteredEvent2),
          depositId2 -> Seq(doiRegisteredEvent2),
          depositId3 -> Seq.empty,
        ).asRight
        (doiActionDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Some(doiActionEvent1),
          depositId2 -> Some(doiActionEvent2),
          depositId3 -> None,
        ).asRight
        (doiActionDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Seq(doiActionEvent1, doiActionEvent2),
          depositId2 -> Seq(doiActionEvent2),
          depositId3 -> Seq.empty,
        ).asRight
        (curationDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Some(curation1),
          depositId2 -> Some(curation2),
          depositId3 -> None,
        ).asRight
        (curationDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Seq(curation1),
          depositId2 -> Seq(curation1, curation2),
          depositId3 -> Seq.empty,
        ).asRight
        (springfieldDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Some(springfield1),
          depositId2 -> Some(springfield2),
          depositId3 -> None,
        ).asRight
        (springfieldDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Seq(springfield1),
          depositId2 -> Seq(springfield2, springfield1),
          depositId3 -> Seq.empty,
        ).asRight
        (contentTypeDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Some(contentType1),
          depositId2 -> Some(contentType2),
          depositId3 -> None,
        ).asRight
        (contentTypeDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
          depositId1 -> Seq(contentType1),
          depositId2 -> Seq(contentType2, contentType1),
          depositId3 -> Seq.empty,
        ).asRight
      }
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listAllDepositsWithAllCurators/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listAllDepositsWithAllCurators" / "plain.graphql"

    inSequence {
      val filters = DepositFilters()
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (curationDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Seq(curation2, curation1),
        depositId2 -> Seq(curation2, curation1),
        depositId3 -> Seq(curation2, curation1),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listAllDepositsWithAllIngestSteps/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listAllDepositsWithAllIngestSteps" / "plain.graphql"

    inSequence {
      val filters = DepositFilters()
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (ingestStepDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Seq(step1, step2),
        depositId2 -> Seq(step2, step3),
        depositId3 -> Seq(step3, step1),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listAllDepositsWithAllStates/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listAllDepositsWithAllStates" / "plain.graphql"

    inSequence {
      val filters = DepositFilters()
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (stateDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Seq(state1, state2),
        depositId2 -> Seq(state2, state3),
        depositId3 -> Seq(state3, state1),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithContentType/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithContentType" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(contentTypeFilter = Some(DepositContentTypeFilter(ContentTypeValue.ZIP)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (contentTypeDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Some(contentType1),
        depositId2 -> Some(contentType2),
        depositId3 -> None,
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithCurationPerformed/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithCurationPerformed" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(curationPerformedFilter = Some(DepositCurationPerformedFilter(curationPerformed = true)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (curationDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Seq(curation1),
        depositId2 -> Seq(curation2),
        depositId3 -> Seq.empty,
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithCurationRequired/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithCurationRequired" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(curationRequiredFilter = Some(DepositCurationRequiredFilter(curationRequired = true)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (curationDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Seq(curation1),
        depositId2 -> Seq(curation2),
        depositId3 -> Seq.empty,
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithCurator/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithCurator" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(curatorFilter = Some(DepositCuratorFilter("archie001")))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (curationDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Seq(curation1),
        depositId2 -> Seq(curation2),
        depositId3 -> Seq.empty,
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithDoiAction/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithDoiAction" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(doiActionFilter = Some(DepositDoiActionFilter(DoiAction.CREATE, SeriesFilter.LATEST)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (doiActionDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Some(doiActionEvent1),
        depositId2 -> Some(doiActionEvent2),
        depositId3 -> None,
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithDoiRegistered/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithDoiRegistered" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(doiRegisteredFilter = Some(DepositDoiRegisteredFilter(value = true, SeriesFilter.LATEST)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (doiRegisteredDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Some(doiRegisteredEvent1),
        depositId2 -> Some(doiRegisteredEvent2),
        depositId3 -> None,
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithIngestStepFilterAll/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithIngestStepFilterAll" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.VALIDATE, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Some(state1),
        depositId2 -> Some(state2),
        depositId3 -> Some(state3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithIsNewVersion/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithIsNewVersion" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(isNewVersionFilter = Some(DepositIsNewVersionFilter(isNewVersion = false)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (curationDao.getAll(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Seq(curation1),
        depositId2 -> Seq(curation2),
        depositId3 -> Seq.empty,
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithState/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithState" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.ARCHIVED, SeriesFilter.LATEST)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
      (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId3) once() returning Seq(
        depositId1 -> Some(state1),
        depositId3 -> Some(state3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithStateFilterAll/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithStateFilterAll" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.DRAFT, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
      (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId3) once() returning Seq(
        depositId1 -> Some(state1),
        depositId3 -> Some(state3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/listDepositsWithStateFilterAllAndIngestStepFilterAll/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "listDepositsWithStateFilterAllAndIngestStepFilterAll" / "plain.graphql"

    inSequence {
      val filters = DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.REJECTED, SeriesFilter.ALL)), ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.VALIDATE, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2, deposit3),
      ).asRight
      (stateDao.getCurrent(_: Seq[DepositId])) expects Seq(depositId1, depositId2, depositId3) once() returning Seq(
        depositId1 -> Some(state1),
        depositId2 -> Some(state2),
        depositId3 -> Some(state3),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'deposits/pagination/firstPage.graphql' with 1 call to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "pagination" / "firstPage.graphql"

    val filters = DepositFilters()
    depositDao.search _ expects Seq(filters) once() returning Seq(
      filters -> Seq(deposit1, deposit2, deposit3, deposit1, deposit2),
    ).asRight

    runQuery(input)
  }

  it should "resolve 'deposits/pagination/secondPage.graphql' with 1 call to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "pagination" / "secondPage.graphql"

    val filters = DepositFilters()
    depositDao.search _ expects Seq(filters) once() returning Seq(
      filters -> Seq(deposit1, deposit2, deposit3, deposit1, deposit2),
    ).asRight

    runQuery(input)
  }

  it should "resolve 'deposits/pagination/thirdPage.graphql' with 1 call to the repository" in {
    val input = graphqlExamplesDir / "deposits" / "pagination" / "thirdPage.graphql"

    val filters = DepositFilters()
    depositDao.search _ expects Seq(filters) once() returning Seq(
      filters -> Seq(deposit1, deposit2, deposit3, deposit1, deposit2),
    ).asRight

    runQuery(input)
  }

  it should "resolve 'identifier/findWithTypeAndValue/plain.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "identifier" / "findWithTypeAndValue" / "plain.graphql"

    inSequence {
      identifierDao.getByTypeAndValue _ expects(IdentifierType.DOI, "10.5072/dans-a1b-cde2") once() returning Some(identifier2).asRight
      identifierDao.getDepositById _ expects identifier2.id once() returning Some(deposit1).asRight
    }

    runQuery(input)
  }

  it should "resolve 'node/onContentType.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "node" / "onContentType.graphql"

    inSequence {
      contentTypeDao.getById _ expects "11" once() returning Some(contentType1).asRight
      contentTypeDao.getDepositById _ expects contentType1.id once() returning Some(deposit1).asRight
      val filters = DepositFilters(contentTypeFilter = Some(DepositContentTypeFilter(ContentTypeValue.ZIP, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'node/onCurator.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "node" / "onCurator.graphql"

    inSequence {
      curationDao.getById _ expects "51" once() returning Some(curation1).asRight
      curationDao.getDepositById _ expects curation1.id once() returning Some(deposit1).asRight
      val filters = DepositFilters(curatorFilter = Some(DepositCuratorFilter("archie001", SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'node/onDeposit.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "node" / "onDeposit.graphql"

    inSequence {
      depositDao.find _ expects depositId2 once() returning deposit2.asRight
      stateDao.getCurrent _ expects Seq(depositId2) once() returning Seq(
        depositId2 -> Some(state2)
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'node/onIdentifier.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "node" / "onIdentifier.graphql"

    inSequence {
      identifierDao.getById _ expects "12" once() returning Some(identifier2).asRight
      identifierDao.getDepositById _ expects identifier2.id once() returning Some(deposit1).asRight
    }

    runQuery(input)
  }

  it should "resolve 'node/onIngestStep.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "node" / "onIngestStep.graphql"

    inSequence {
      ingestStepDao.getById _ expects "10" once() returning Some(step1).asRight
      ingestStepDao.getDepositById _ expects step1.id once() returning Some(deposit1).asRight
      val filters = DepositFilters(ingestStepFilter = Some(DepositIngestStepFilter(IngestStepLabel.VALIDATE, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit2),
      ).asRight
    }

    runQuery(input)
  }

  it should "resolve 'node/onSpringfield.graphql' with 2 calls to the repository" in {
    val input = graphqlExamplesDir / "node" / "onSpringfield.graphql"

    inSequence {
      springfieldDao.getById _ expects "10" once() returning Some(springfield1).asRight
      springfieldDao.getDepositById _ expects springfield1.id once() returning Some(deposit1).asRight
    }

    runQuery(input)
  }

  it should "resolve 'node/onState.graphql' with 3 calls to the repository" in {
    val input = graphqlExamplesDir / "node" / "onState.graphql"

    inSequence {
      stateDao.getById _ expects "15" once() returning Some(state2).asRight
      stateDao.getDepositById _ expects state2.id once() returning Some(deposit1).asRight
      val filters = DepositFilters(stateFilter = Some(DepositStateFilter(StateLabel.DRAFT, SeriesFilter.ALL)))
      depositDao.search _ expects Seq(filters) once() returning Seq(
        filters -> Seq(deposit1, deposit3),
      ).asRight
    }

    runQuery(input)
  }

  private def runQuery(graphQLFile: File): Unit = {
    assume(graphQLFile.exists, s"input file does not exist: $graphQLFile")
    runQuery(graphQLFile.contentAsString)
  }

  private def runQuery(query: String): Unit = {
    val inputBody = compact(render("query" -> query))
    post(uri = "/", body = inputBody.getBytes) {
      parse(body) \ "errors" match {
        case JNothing => // do nothing, no errors
        case jsonErrors => read[Seq[Error]](compact(render(jsonErrors))) shouldBe empty
      }

      status shouldBe 200
    }
  }

  case class Location(line: Int, column: Int)
  case class Error(message: String, path: Seq[String], locations: Seq[Location])
}
