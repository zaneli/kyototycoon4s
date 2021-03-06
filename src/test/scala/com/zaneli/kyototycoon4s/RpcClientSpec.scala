package com.zaneli.kyototycoon4s

import com.github.nscala_time.time.Imports.DateTime
import com.zaneli.kyototycoon4s.Implicits._
import com.zaneli.kyototycoon4s.rpc.{Encoder, Origin}
import java.nio.ByteBuffer
import java.util.Arrays
import org.scalatest.FunSpec
import scala.math.abs
import scalaj.http.Http

class RpcClientSpec extends FunSpec with ClientSpecBase {

  override protected[this] val (host, port) = ("localhost", 1978)

  private[this] val client = KyotoTycoonClient.rpc(host, port)

  describe("void") {
    it("no params") {
      val res = client.void()
      assert(res.isSuccess)
    }
  }

  describe("echo") {
    it("no params") {
      val res = client.echo()()
      assert(res.isSuccess)
      res.foreach { params =>
        assert(params.isEmpty)
      }
    }
    it("one param") {
      val res = client.echo(("key", 123))()
      assert(res.isSuccess)
      res.foreach { params =>
        assert(params.size === 1)
        assert(params.head === (("key", "123")))
      }
    }
    it("some params") {
      val res = client.echo(("key1", 123), ("key2", "abc"), ("key3", "xyz"))()
      assert(res.isSuccess)
      res.foreach { params =>
        assert(params.size === 3)
        assert(params.contains(("key1", "123")))
        assert(params.contains(("key2", "abc")))
        assert(params.contains(("key3", "xyz")))
      }
    }
    it("base64 encode param") {
      val res = client.echo(("a\tb\nc\t", "z\ty\nz\t"))(Encoder.Base64)
      assert(res.isSuccess)
      res.foreach { params =>
        assert(params.size === 1)
        assert(params.head === (("a\tb\nc\t", "z\ty\nz\t")))
      }
    }
    it("url encode param") {
      val res = client.echo(("a\tb\nc\t", "z\ty\nz\t"))(Encoder.URL)
      assert(res.isSuccess)
      res.foreach { params =>
        assert(params.size === 1)
        assert(params.head === (("a\tb\nc\t", "z\ty\nz\t")))
      }
    }
  }

  describe("report") {
    it("no params") {
      val res = client.report()
      assert(res.isSuccess)
    }
  }

  describe("status") {
    it("no params") {
      val res = client.status()
      assert(res.isSuccess)
      res.foreach { status =>
        assert(status.count >= 0)
        assert(status.size >= 0)
        assert(status.params.nonEmpty)
      }
    }
  }

  describe("clear") {
    it("no params") {
      val key1 = asKey("key1_for_clear")
      val key2 = asKey("key2_for_clear")
      prepare(key1, "value1")
      prepare(key2, "value2")
      assert(Http(restUrl(key1)).asString.isNotError)
      assert(Http(restUrl(key2)).asString.isNotError)

      val res = client.clear()
      assert(res.isSuccess)

      assert(Http(restUrl(key1)).asString.code === 404)
      assert(Http(restUrl(key2)).asString.code === 404)
    }
  }

  describe("set") {
    it("set value without xt") {
      val key = asKey("test_key_for_set_without_xt")
      val value = "test_value_for_set_without_xt"
      assert(client.set(key, value).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assert(getXt(res.headers).isEmpty)
    }
    it("set value with xt") {
      val key = asKey("test_key_for_set_with_xt")
      val value = "test_value_for_set_with_xt"
      val xt = DateTime.now.withMillisOfSecond(0).plusSeconds(30)
      assert(client.set(key, value, Some(30)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assertWithin(getXt(res.headers), xt)
    }
    it("set value (require url encode)") {
      val key = asKey("te\tst/key\n_for_set?=%~")
      val value = "te\tst/value\n_for_set?=%~"
      assert(client.set(key, value, encoder = Some(Encoder.URL)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assert(getXt(res.headers).isEmpty)
    }
    it("set long value") {
      val key = asKey("test_key_for_set_long")
      val value = 1L
      assert(client.set(key, value).isSuccess)

      val res = Http(restUrl(key)).asBytes
      assert(res.isNotError)
      assert(ByteBuffer.wrap(res.body).getLong === value)
      assert(getXt(res.headers).isEmpty)
    }
  }

  describe("add") {
    it("add value without xt") {
      val key = asKey("test_key_for_add_without_xt")
      val value = "test_value_for_add_without_xt"
      assert(client.add(key, value).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assert(getXt(res.headers).isEmpty)
    }
    it("add value with xt") {
      val key = asKey("test_key_for_add_with_xt")
      val value = "test_value_for_add_with_xt"
      val xt = DateTime.now.withMillisOfSecond(0).plusSeconds(30)
      assert(client.add(key, value, Some(30)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assertWithin(getXt(res.headers), xt)
    }
    it("add value (require url encode)") {
      val key = asKey("te\tst/key\n_for_add?=%~")
      val value = "te\tst/value\n_for_add?=%~"
      assert(client.add(key, value, encoder = Some(Encoder.URL)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assert(getXt(res.headers).isEmpty)
    }
    it("add long value") {
      val key = asKey("test_key_for_add_long")
      val value = Long.MinValue
      assert(client.add(key, value).isSuccess)

      val res = Http(restUrl(key)).asBytes
      assert(res.isNotError)
      assert(ByteBuffer.wrap(res.body).getLong === value)
      assert(getXt(res.headers).isEmpty)
    }
    it("add value (already key exists)") {
      val key = asKey("test_key_for_add")
      val value = "test_value_for_add"
      prepare(key, "prepared_value")

      val res = client.add(key, value)
      assert(res.isFailure)
      res.failed.foreach(t => assert(t.getMessage == "450: DB: 6: record duplication: record duplication"))
    }
  }

  describe("replace") {
    it("replace value without xt") {
      val key = asKey("test_key_for_replace_without_xt")
      val value = "test_value_for_replace_without_xt"
      prepare(key, "prepared_value")
      assert(client.replace(key, value).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assert(getXt(res.headers).isEmpty)
    }
    it("replace value with xt") {
      val key = asKey("test_key_for_replace_with_xt")
      val value = "test_value_for_replace_with_xt"
      prepare(key, "prepared_value")
      val xt = DateTime.now.withMillisOfSecond(0).plusSeconds(30)
      assert(client.replace(key, value, Some(30)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assertWithin(getXt(res.headers), xt)
    }
    it("replace value (require url encode)") {
      val key = asKey("te\tst/key\n_for_replace?=%~")
      val value = "te\tst/value\n_for_replace?=%~"
      prepare(key, "prepared_value")
      assert(client.replace(key, value, encoder = Some(Encoder.URL)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assert(getXt(res.headers).isEmpty)
    }
    it("replace long value") {
      val key = asKey("test_key_for_replace_long")
      val value = 0L
      prepare(key, "prepared_value")
      assert(client.replace(key, value).isSuccess)

      val res = Http(restUrl(key)).asBytes
      assert(res.isNotError)
      assert(ByteBuffer.wrap(res.body).getLong === value)
      assert(getXt(res.headers).isEmpty)
    }
    it("replace value (key not exists)") {
      val key = asKey("test_key_for_replace")
      val value = "test_value_for_replace"

      val res = client.replace(key, value)
      assert(res.isFailure)
      res.failed.foreach(t => assert(t.getMessage == "450: DB: 7: no record: no record"))
    }
  }

  describe("append") {
    it("append value without xt") {
      val key = asKey("test_key_for_append_without_xt")
      val value = "test_value_for_append_without_xt"
      prepare(key, "prepared_value")
      assert(client.append(key, value).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === "prepared_value" + value)
      assert(getXt(res.headers).isEmpty)
    }
    it("append value with xt") {
      val key = asKey("test_key_for_append_with_xt")
      val value = "test_value_for_append_with_xt"
      prepare(key, "prepared_value")
      val xt = DateTime.now.withMillisOfSecond(0).plusSeconds(30)
      assert(client.append(key, value, Some(30)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === "prepared_value" + value)
      assertWithin(getXt(res.headers), xt)
    }
    it("append value (require url encode)") {
      val key = asKey("te\tst/key\n_for_append?=%~")
      val value = "te\tst/value\n_for_append?=%~"
      prepare(key, "prepared_value")
      assert(client.append(key, value, encoder = Some(Encoder.URL)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === "prepared_value" + value)
      assert(getXt(res.headers).isEmpty)
    }
    it("append value (key not exists)") {
      val key = asKey("test_key_for_append")
      val value = "test_value_for_append"
      assert(client.append(key, value).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assert(getXt(res.headers).isEmpty)
    }
  }

  describe("increment") {
    it("increment value") {
      val key = asKey("test_key_for_increment")
      val value = 10L
      prepare(key, 1L)

      val res = client.increment(key, value)
      assert(res.isSuccess)
      res.foreach(l => assert(l === 11L))
    }
    it("increment value (key not exists)") {
      val key = asKey("test_key_for_increment_key_not_found")
      val value = 1L

      val res = client.increment(key, value)
      assert(res.isSuccess)
      res.foreach(l => assert(l === 1L))
    }
    it("increment value (orig=num)") {
      val key = asKey("test_key_for_increment_orig_num")
      val value = 11L

      val res = client.increment(key, value, orig = Some(Origin.num(22L)))
      assert(res.isSuccess)
      res.foreach(l => assert(l === 33L))
    }
    it("increment value (orig=set)") {
      val key = asKey("test_key_for_increment_orig_set")
      val value = 5L

      val res = client.increment(key, value, orig = Some(Origin.Set))
      assert(res.isSuccess)
      res.foreach(l => assert(l === 5L))
    }
    it("increment value (orig=try)") {
      val key = asKey("test_key_for_increment_orig_try")
      val value = 5L

      val res = client.increment(key, value, orig = Some(Origin.Try))
      assert(res.isFailure)
      res.failed.foreach(t => assert(t.getMessage === "450: DB: 8: logical inconsistency: logical inconsistency"))
    }
  }

  describe("increment_double") {
    it("increment_double value") {
      val key = asKey("test_key_for_increment_double")
      val value = 12.34D
      prepare(key, Array(0, 0, 0, 0, 0, 0, 0, 10, 0, 1, -58, -65, 82, 99, 64, 0))(_.map(_.toByte)) // 10.5D

      val res = client.incrementDouble(key, value)
      assert(res.isSuccess)
      res.foreach(d => assert(d === 22.84D))
    }
    it("increment_double value (key not exists)") {
      val key = asKey("test_key_for_increment_double_key_not_found")
      val value = 55.55D

      val res = client.incrementDouble(key, value)
      assert(res.isSuccess)
      res.foreach(d => assert(d === 55.55D))
    }
    it("increment_double value (orig=num)") {
      val key = asKey("test_key_for_increment_double_orig_num")
      val value = 12.3D

      val res = client.incrementDouble(key, value, orig = Some(Origin.num(0.1D)))
      assert(res.isSuccess)
      res.foreach(d => assert(d === 12.4D))
    }
    it("increment_double value (orig=set)") {
      val key = asKey("test_key_for_increment_double_orig_set")
      val value = 12345.67D

      val res = client.incrementDouble(key, value, orig = Some(Origin.Set))
      assert(res.isSuccess)
      res.foreach(d => assert(d === 12345.67D))
    }
    it("increment_double value (orig=try)") {
      val key = asKey("test_key_for_increment_double_orig_try")
      val value = 12345.67D

      val res = client.incrementDouble(key, value, orig = Some(Origin.Try))
      assert(res.isFailure)
      res.failed.foreach(t => assert(t.getMessage === "450: DB: 8: logical inconsistency: logical inconsistency"))
    }
  }

  describe("cas") {
    it("swap old value") {
      val key = asKey("test_key_for_cas_swap")
      val value = "test_value_for_cas_swap"
      prepare(key, "prepared_value")
      assert(client.cas(key, oval = Some("prepared_value"), nval = Some(value)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
    }
    it("swap old value with xt") {
      val key = asKey("test_key_for_cas_swap_with_xt")
      val value = "test_value_for_cas_swap_with_xt"
      prepare(key, "prepared_value")
      val xt = DateTime.now.withMillisOfSecond(0).plusSeconds(30)
      assert(client.cas(key, oval = Some("prepared_value"), nval = Some(value), xt = Some(30)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
      assertWithin(getXt(res.headers), xt)
    }
    it("remove old value") {
      val key = asKey("test_key_for_cas_remove")
      prepare(key, "prepared_value")
      assert(client.cas(key, oval = Some("prepared_value")).isSuccess)

      assert(Http(restUrl(key)).asString.code === 404)
    }
    it("create new value") {
      val key = asKey("test_key_for_cas_create")
      val value = "test_value_for_cas_create"
      assert(client.cas(key, nval = Some(value)).isSuccess)

      val res = Http(restUrl(key)).asString
      assert(res.isNotError)
      assert(res.body === value)
    }
    it("failed swap changed value") {
      val key = asKey("test_key_for_cas_swap")
      val value = "test_value_for_cas_swap"
      prepare(key, "changed_value")
      val res1 = client.cas(key, oval = Some("prepared_value"), nval = Some(value))
      assert(res1.isFailure)
      res1.failed.foreach(t => assert(t.getMessage === "450: DB: 8: logical inconsistency: status conflict"))

      val res2 = Http(restUrl(key)).asString
      assert(res2.isNotError)
      assert(res2.body === "changed_value")
    }
  }

  describe("remove") {
    it("remove value") {
      val key = asKey("test_key_for_remove")
      prepare(key, "prepared_value")
      assert(client.remove(key).isSuccess)

      assert(Http(restUrl(key)).asString.code === 404)
    }
    it("remove value (key not exists)") {
      val key = asKey("test_key_for_remove_key_not_found")
      val res = client.remove(key)
      assert(res.isFailure)
      res.failed.foreach(t => assert(t.getMessage === "450: DB: 7: no record: no record"))
    }
  }

  describe("get") {
    it("as string") {
      val key = asKey("test_key_for_get_string")
      val value = "test_value_for_get_string"
      prepare(key, value)

      val res = client.get(key)
      assert(res.isSuccess)
      res.foreach { r =>
        assert(r.value === value)
        assert(r.xt.isEmpty)
      }
    }
    it("as byte array") {
      val key = asKey("test_key_for_get_bytes")
      val value = "test_value_for_get_bytes"
      prepare(key, value)

      val res = client.get(key, as = identity)
      assert(res.isSuccess)
      res.foreach { r =>
        assert(Arrays.equals(r.value, value))
        assert(r.xt.isEmpty)
      }
    }
    it("with xt exists") {
      val key = asKey("test_key_for_get_with_xt")
      val value = "test_value_for_get_with_xt"
      val xt = DateTime.now.plusMinutes(10)
      prepare(key, value, Some(xt.getMillis / 1000))

      val res = client.get(key)
      assert(res.isSuccess)
      res.foreach { r =>
        assert(r.value === value)
        assert(r.xt.exists(_.getMillis == xt.withMillisOfSecond(0).getMillis))
      }
    }
    it("key require url encode") {
      val key = asKey("te\tst/key _for_get\n?=%~")
      val value = "te\tst/key _for_get\n?=%~"
      prepare(key, value)
      val res = client.get(key, encoder = Encoder.URL)
      assert(res.isSuccess)
      res.foreach { r =>
        assert(r.value === value)
        assert(r.xt.isEmpty)
      }
    }
    it("key not exists") {
      val res = client.get("test_key_for_get_not_found")
      assert(res.isFailure)
      assert(res.failed.get.getMessage === "450: DB: 7: no record: no record")
    }
  }

  describe("check") {
    it("check value") {
      val key = asKey("test_key_for_check_without_xt")
      prepare(key, "prepared_value")
      val res = client.check(key)
      assert(res.isSuccess)
      res.foreach { case (s, x) =>
        assert(s === "prepared_value".length)
        assert(x.isEmpty)
      }
    }
    it("check value with xt") {
      val key = asKey("test_key_for_check_with_xt")
      val xt = DateTime.now.plusMinutes(10)
      prepare(key, "prepared_value", Some(xt.getMillis / 1000))
      val res = client.check(key)
      assert(res.isSuccess)
      res.foreach { case (s, x) =>
        assert(s === "prepared_value".length)
        assert(x.exists(_.getMillis == xt.withMillisOfSecond(0).getMillis))
      }
    }
    it("check value (key not exists)") {
      val key = asKey("test_key_for_check_key_not_found")
      val res = client.check(key)
      assert(res.isFailure)
      res.failed.foreach(t => assert(t.getMessage === "450: DB: 7: no record: no record"))
    }
  }

  describe("seize") {
    it("as string") {
      val key = asKey("test_key_for_seize")
      val value = "test_value_for_seize"
      prepare(key, value)

      val res = client.seize(key)
      assert(res.isSuccess)
      res.foreach { r =>
        assert(r.value === value)
        assert(r.xt.isEmpty)
      }

      assert(Http(restUrl(key)).asString.code === 404)
    }
    it("key not exists") {
      val res = client.seize("test_key_for_seize_not_found")
      assert(res.isFailure)
      assert(res.failed.get.getMessage === "450: DB: 7: no record: no record")
    }
  }

  describe("set_bulk") {
    it("as string values") {
      val key1 = asKey("test_key_for_set_bulk_string_1")
      val value1 = "test_value_for_set_bulk_string_1"
      val key2 = asKey("test_key_for_set_bulk_string_2")
      val value2 = "test_value_for_set_bulk_string_2"
      val key3 = asKey("test_key_for_set_bulk_string_3")
      val value3 = "test_value_for_set_bulk_string_3"
      val res = client.setBulk((key1, value1), (key2, value2), (key3, value3))()
      assert(res.isSuccess)
      res.foreach(num => assert(num === 3))

      (1 to 3).foreach { i =>
        val res = Http(restUrl(s"test_key_for_set_bulk_string_$i")).asString
        assert(res.isNotError)
        assert(res.body === s"test_value_for_set_bulk_string_$i")
        assert(getXt(res.headers).isEmpty)
      }
    }
    it("as byte array values") {
      val records = (1 to 3).map { i =>
        val k = asKey(s"test_key_for_set_bulk_byte_array_$i")
        val v = s"test_value_for_set_bulk_byte_array_$i".getBytes("UTF-8")
        (k, v)
      }
      val res = client.setBulk(records: _*)()
      assert(res.isSuccess)
      res.foreach(num => assert(num === records.size))

      records.foreach { case (k, v) =>
        val res = Http(restUrl(k)).asBytes
        assert(res.isNotError)
        assert(Arrays.equals(res.body, v))
        assert(getXt(res.headers).isEmpty)
      }
    }
    it("with xt") {
      val key = asKey("test_key_for_set_bulk_with_xt")
      val value = "test_value_for_set_bulk_with_xt"
      val xt = DateTime.now.withMillisOfSecond(0).plusSeconds(120)
      val res1 = client.setBulk((key, value))(xt = Some(120))
      assert(res1.isSuccess)
      res1.foreach(num => assert(num === 1))

      val res2 = Http(restUrl(key)).asString
      assert(res2.isNotError)
      assert(res2.body === value)
      assertWithin(getXt(res2.headers), xt)
    }
  }

  describe("remove_bulk") {
    it("remove records") {
      val keys = (1 to 3).map { i =>
        val k = asKey(s"test_key_for_remove_bulk_$i")
        val v = s"test_value_for_remove_bulk_$i"
        prepare(k, v)
        k
      }
      val res = client.removeBulk(keys: _*)(atomic = true)
      assert(res.isSuccess)
      res.foreach(num => assert(num === keys.size))

      keys.foreach(k => assert(Http(restUrl(k)).asString.code === 404))
    }
  }

  describe("get_bulk") {
    it("get records") {
      val kvs = (1 to 3).map { i =>
        val k = asKey(s"test_key_for_remove_bulk_$i")
        val v = s"test_value_for_remove_bulk_$i"
        prepare(k, v)
        (k, v)
      }
      val res = client.getBulk(kvs.map(_._1): _*)(atomic = false)
      assert(res.isSuccess)
      res.foreach { records =>
        assert(records.size === kvs.size)
        kvs.foreach { case (k, v) =>
          assert(records.get(k).contains(v))
        }
      }
    }
  }

  describe("vacuum") {
    it("no params") {
      val res = client.vacuum()
      assert(res.isSuccess)
    }
    it("set step") {
      val res = client.vacuum(step = Some(1))
      assert(res.isSuccess)
    }
  }

  describe("match_prefix") {
    def prepareRecords(prefix: String): Seq[String] = {
      val keys = (1 to 3).map { i =>
        val k = asKey(s"$prefix$i")
        val v = s"test_value_for_match_prefix_$i"
        prepare(k, v)
        k
      }
      prepare(asKey(s"other_prefix_$prefix"), "other_prefix_value")
      keys
    }
    it("no params") {
      val prefix = "test_key_for_match_prefix_"
      val keys = prepareRecords(prefix)
      val res = client.matchPrefix(prefix)
      assert(res.isSuccess)
      res.foreach { records =>
        assert(records.size === keys.size)
        assert(records.keys.forall(keys.contains))
      }
    }
    it("set max") {
      val prefix = "test_key_for_match_prefix_set_max_"
      val keys = prepareRecords(prefix)
      val res = client.matchPrefix(prefix, max = Some(2))
      assert(res.isSuccess)
      res.foreach { records =>
        assert(records.size === 2)
        assert(records.keys.forall(keys.contains))
      }
    }
  }

  describe("match_regex") {
    def prepareRecords(prefix: String): Seq[String] = {
      val key1 = asKey(s"$prefix a")
      prepare(key1, "prepared_value")
      val key2 = asKey(s"${prefix}xy")
      prepare(key2, "prepared_value")
      val key3 = asKey(s"$prefix b")
      prepare(key3, "prepared_value")
      val key4 = asKey(s"${prefix}12")
      prepare(key4, "prepared_value")
      Seq(key1, key2, key3, key4)
    }
    it("no params") {
      val prefix = "test_key_for_match_regex_"
      val keys = prepareRecords(prefix)
      val res = client.matchRegex(prefix + """(\s)(\w)""", encoder = Encoder.Base64)
      assert(res.isSuccess)
      res.foreach { records =>
        assert(records.size === 2)
        assert(records.keySet.contains(keys.head))
        assert(records.keySet.contains(keys(2)))
      }
    }
    it("set max") {
      val prefix = "test_key_for_match_regex_set_max_"
      val keys = prepareRecords(prefix)
      val res = client.matchRegex(s"$prefix.+", encoder = Encoder.Base64, max = Some(2))
      assert(res.isSuccess)
      res.foreach { records =>
        assert(records.size === 2)
        assert(records.keySet.contains(keys.head))
        assert(records.keySet.contains(keys(1)))
      }
    }
  }

  describe("match_similar") {
    def prepareRecords(origin: String): Seq[String] = {
      val key1 = asKey(s"${origin}1")
      prepare(key1, "prepared_value")
      val key2 = asKey(s"${origin}12")
      prepare(key2, "prepared_value")
      val key3 = asKey(s"${origin.tail}")
      prepare(key3, "prepared_value")
      Seq(key1, key2, key3)
    }
    it("no params") {
      val origin = "test_key_for_match_similar_"
      val keys = prepareRecords(origin)
      val res = client.matchSimilar(origin)
      assert(res.isSuccess)
      res.foreach { records =>
        assert(records.size === 2)
        assert(records.keySet.contains(keys.head))
        assert(records.keySet.contains(keys(2)))
      }
    }
    it("set range") {
      val origin = "test_key_for_match_similar_set_range_"
      val keys = prepareRecords(origin)
      val res = client.matchSimilar(origin, range = 2)
      assert(res.isSuccess)
      res.foreach { records =>
        assert(records.size === 3)
        assert(keys.forall(records.keySet.contains))
      }
    }
    it("set max") {
      val origin = "test_key_for_match_similar_set_max_"
      val keys = prepareRecords(origin)
      val res = client.matchSimilar(origin, max = Some(1))
      assert(res.isSuccess)
      res.foreach { records =>
        assert(records.size === 1)
        assert(records.keySet.forall(keys.contains))
      }
    }
  }

  private[this] def assertWithin(actual: Option[DateTime], expected: DateTime, ms: Long = 1000L): Unit = {
    assert(actual.map(_.getMillis - expected.getMillis).exists(abs(_) <= ms))
  }
}
