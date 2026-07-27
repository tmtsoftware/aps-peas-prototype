package org.tmt.peasproceduredataservice.http

import csw.location.api.codec.LocationCodecs
import io.bullet.borer.{Codec, Decoder, Encoder}
import io.bullet.borer.compat.PekkoHttpCompat
import io.bullet.borer.derivation.MapBasedCodecs.deriveCodec
import org.tmt.peasproceduredataservice.core.models.{
  AdminGreetResponse,
  ComputationKeyValuePair,
  ComputationKeyValuePairKey,
  ComputationKeyValuePairList,
  ComputationResultKey,
  GenericValue,
  GetProcedureResultDataRequest,
  GreetResponse,
  UserInfo
}

import java.util.{List as JList, Optional}
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

// #for-docs-snippet
object HttpCodecs extends HttpCodecs
// #for-docs-snippet

trait HttpCodecs extends PekkoHttpCompat with LocationCodecs {

  // Codec for java.util.Optional — borer does not derive this automatically
  implicit def javaOptionalEncoder[A: Encoder]: Encoder[Optional[A]] =
    Encoder { (writer, opt) =>
      if (opt.isPresent) summon[Encoder[A]].write(writer, opt.get())
      else writer.writeNull()
    }

  implicit def javaOptionalDecoder[A: Decoder]: Decoder[Optional[A]] =
    Decoder { reader =>
      if (reader.tryReadNull()) Optional.empty[A]()
      else Optional.of(summon[Decoder[A]].read(reader))
    }

  // Codec for java.util.List — borer does not derive this automatically
  implicit def javaListEncoder[A: Encoder]: Encoder[JList[A]] =
    Encoder { (writer, list) =>
      summon[Encoder[Seq[A]]].write(writer, list.asScala.toSeq)
    }

  implicit def javaListDecoder[A: Decoder]: Decoder[JList[A]] =
    Decoder { reader =>
      summon[Decoder[Seq[A]]].read(reader).asJava
    }

  // Existing codecs
  implicit lazy val greetResponseCodec: Codec[GreetResponse]           = deriveCodec
  implicit lazy val adminGreetResponseCodec: Codec[AdminGreetResponse] = deriveCodec
  implicit lazy val userInfoCodec: Codec[UserInfo]                     = deriveCodec

  // New codecs — leaf types before composite types
  implicit lazy val genericValueCodec: Codec[GenericValue]                          = deriveCodec
  implicit lazy val computationResultKeyCodec: Codec[ComputationResultKey]          = deriveCodec
  implicit lazy val computationKvpKeyCodec: Codec[ComputationKeyValuePairKey]       = deriveCodec
  implicit lazy val computationKvpCodec: Codec[ComputationKeyValuePair]             = deriveCodec
  implicit lazy val computationKvpListCodec: Codec[ComputationKeyValuePairList]     = deriveCodec
  implicit lazy val getProcResultDataReqCodec: Codec[GetProcedureResultDataRequest] = deriveCodec
}
