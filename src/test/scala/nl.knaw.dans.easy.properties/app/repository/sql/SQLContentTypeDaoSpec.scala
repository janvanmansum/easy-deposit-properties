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
package nl.knaw.dans.easy.properties.app.repository.sql

import java.util.UUID

import cats.scalatest.{ EitherMatchers, EitherValues }
import nl.knaw.dans.easy.properties.app.model.contentType.{ ContentType, ContentTypeValue, InputContentType }
import nl.knaw.dans.easy.properties.app.repository.{ DepositIdAndTimestampAlreadyExistError, InvalidValueError, NoSuchDepositError }
import nl.knaw.dans.easy.properties.fixture.{ DatabaseDataFixture, DatabaseFixture, FileSystemSupport, TestSupportFixture }
import org.joda.time.DateTime

class SQLContentTypeDaoSpec extends TestSupportFixture
  with FileSystemSupport
  with DatabaseFixture
  with DatabaseDataFixture
  with EitherValues
  with EitherMatchers {

  "getById" should "find content types identified by their id" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getById(Seq("27", "29", "31")).value should contain only(
      "27" -> Some(contentType1),
      "29" -> Some(contentType3),
      "31" -> Some(contentType5),
    )
  }

  it should "return a None if the id is unknown" in {
    val contentTypes = new SQLContentTypeDao
    val unknownId = "102"

    contentTypes.getById(Seq(unknownId)).value should contain only (unknownId -> Option.empty)
  }

  it should "return an empty collection when the input collection is empty" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getById(Seq.empty).value shouldBe empty
  }

  it should "fail when an invalid id is given" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getById(Seq("12", "invalid-id", "29")).leftValue shouldBe InvalidValueError("invalid id 'invalid-id'")
  }

  "getCurrent" should "return the current content type of the given deposits" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getCurrent(Seq(depositId4, depositId5)).value should contain only(
      depositId5 -> Some(contentType5),
      depositId4 -> Some(contentType4),
    )
  }

  it should "return a None if the depositId is unknown" in {
    val contentTypes = new SQLContentTypeDao
    val depositId6 = UUID.fromString("00000000-0000-0000-0000-000000000006")

    contentTypes.getCurrent(Seq(depositId6)).value should contain only (depositId6 -> Option.empty)
  }

  it should "return an empty collection when the input collection is empty" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getCurrent(Seq.empty).value shouldBe empty
  }

  "getAll" should "return all content types associated with the given deposits" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getAll(Seq(depositId1, depositId4)).value should contain only(
      depositId1 -> Seq(contentType0, contentType1),
      depositId4 -> Seq(contentType4),
    )
  }

  it should "return a None if the depositId is unknown" in {
    val contentTypes = new SQLContentTypeDao
    val depositId6 = UUID.fromString("00000000-0000-0000-0000-000000000006")

    contentTypes.getAll(Seq(depositId6)).value should contain only (depositId6 -> Seq.empty)
  }

  it should "return an empty collection when the input collection is empty" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getAll(Seq.empty).value shouldBe empty
  }

  "store" should "insert a new content type into the database" in {
    val contentTypes = new SQLContentTypeDao
    val timestamp = new DateTime(2019, 7, 19, 22, 45, timeZone)
    val inputContentType = InputContentType(ContentTypeValue.OCTET, timestamp)
    val expectedContentType = ContentType("32", ContentTypeValue.OCTET, timestamp)

    contentTypes.store(depositId4, inputContentType).value shouldBe expectedContentType
    contentTypes.getById(Seq("32")).value should contain only ("32" -> Some(expectedContentType))
    contentTypes.getCurrent(Seq(depositId4)).value should contain only (depositId4 -> Some(expectedContentType))
    contentTypes.getAll(Seq(depositId4)).value.toMap.apply(depositId4) should contain(expectedContentType)
  }

  it should "fail when the given depositId does not exist" in {
    val contentTypes = new SQLContentTypeDao
    val depositId6 = UUID.fromString("00000000-0000-0000-0000-000000000006")
    val timestamp = new DateTime(2019, 7, 18, 22, 38, timeZone)
    val inputContentType = InputContentType(ContentTypeValue.OCTET, timestamp)

    contentTypes.store(depositId6, inputContentType).leftValue shouldBe NoSuchDepositError(depositId6)
  }

  it should "fail when the depositId and timestamp combination is already present, even though the other values are different" in {
    val contentTypes = new SQLContentTypeDao
    val depositId = depositId1
    val timestamp = new DateTime(2019, 1, 1, 6, 6, timeZone)
    val inputContentType1 = InputContentType(ContentTypeValue.OCTET, timestamp)
    val inputContentType2 = InputContentType(ContentTypeValue.ZIP, timestamp)

    contentTypes.store(depositId, inputContentType1) shouldBe right
    contentTypes.store(depositId, inputContentType2).leftValue shouldBe DepositIdAndTimestampAlreadyExistError(depositId, timestamp, "content type")
  }

  "getDepositsById" should "find deposits identified by these contentTypeIds" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getDepositsById(Seq("26", "28", "30")).value should contain only(
      "26" -> Some(deposit1),
      "28" -> Some(deposit2),
      "30" -> Some(deposit4),
    )
  }

  it should "return a None if the contentTypeId is unknown" in {
    val contentTypes = new SQLContentTypeDao
    val unknowncontentTypeId = "102"

    contentTypes.getDepositsById(Seq(unknowncontentTypeId)).value should contain only (unknowncontentTypeId -> Option.empty)
  }

  it should "return an empty collection when the input collection is empty" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getDepositsById(Seq.empty).value shouldBe empty
  }

  it should "fail when an invalid contentTypeId is given" in {
    val contentTypes = new SQLContentTypeDao

    contentTypes.getDepositsById(Seq("12", "invalid-id", "29")).leftValue shouldBe InvalidValueError("invalid id 'invalid-id'")
  }
}
