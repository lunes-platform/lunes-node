package io.lunes.utils

import java.io.{File, PrintWriter}
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

import play.api.libs.json.{Json, Reads, Writes}
import scorex.crypto.encode.Base64

import scala.io.{BufferedSource, Source}

/** JSON File Storage definitions Object.*/
object JsonFileStorage {
  private val encoding = "UTF-8"
  private val keySalt = "0495c728-1614-41f6-8ac3-966c22b4a62d"
  private val aes = "AES"
  private val algorithm = aes + "/ECB/PKCS5Padding"
  private val hashing = "PBKDF2WithHmacSHA512"
  private val hashingIterations = 999999
  private val keyLength = 128

  import java.security.NoSuchAlgorithmException
  import java.security.spec.InvalidKeySpecException
  import javax.crypto.SecretKeyFactory
  import javax.crypto.spec.PBEKeySpec

  /** Returns a Hash Password based on the input data.
    * @param password Char Array for the input password.
    * @param salt Java Crypto Salt Spec.
    * @param iterations Number of Iterations.
    * @param keyLength Key Length.
    * @return Returns a Array of Byte for the Hash Password.
    */
  private def hashPassword(password: Array[Char], salt: Array[Byte], iterations: Int, keyLength: Int): Array[Byte] = try {
    val skf = SecretKeyFactory.getInstance(hashing)
    val spec = new PBEKeySpec(password, salt, iterations, keyLength)
    val key = skf.generateSecret(spec)
    val res = key.getEncoded
    res
  } catch {
    case e@(_: NoSuchAlgorithmException | _: InvalidKeySpecException) =>
      throw new RuntimeException(e)
  }

  /** Key Specifications Factory based on a String.
    * @param key Input String.
    * @return Returns a new Java Crypto SecretKeySpecs.
    */
  def prepareKey(key: String): SecretKeySpec =
    new SecretKeySpec(hashPassword(key.toCharArray, keySalt.getBytes(encoding), hashingIterations, keyLength), aes)

  /** Encrypts a Input String under a Specification.
    * @param key Specifications.
    * @param value Input String.
    * @return Returns Encrypted String.
    */
  private def encrypt(key: SecretKeySpec, value: String): String = {
    val cipher: Cipher = Cipher.getInstance(algorithm)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    Base64.encode(cipher.doFinal(value.getBytes(encoding)))
  }

  /** Decrypts a Input Value Based on a Specification.
    * @param key Specifications.
    * @param encryptedValue Input String.
    * @return Returns a String processed for Decryption.
    */
  private def decrypt(key: SecretKeySpec, encryptedValue: String): String = {
    val cipher: Cipher = Cipher.getInstance(algorithm)
    cipher.init(Cipher.DECRYPT_MODE, key)
    new String(cipher.doFinal(Base64.decode(encryptedValue)))
  }

  /** Save data based on a Type Parameter T.
    * @param value Input Value.
    * @param path Filepath String.
    * @param key Option for Specifications for Encryption.
    * @param w Write Object.
    * @tparam T Parametrized Type.
    */
  def save[T](value: T, path: String, key: Option[SecretKeySpec])(implicit w: Writes[T]): Unit = {
    var file: Option[PrintWriter] = None
    try {
      val folder = new File(path).getParentFile
      if (!folder.exists())
        folder.mkdirs()
      file = Option(new PrintWriter(path))
      file.foreach {
        val json = Json.toJson(value).toString()
        val data = key.fold(json)(k => encrypt(k, json))
        _.write(data)
      }
    }
    finally {
      file.foreach(_.close())
    }
  }

  /** Saves data without Encryption Specifications.
    * @param value Input Value.
    * @param path Filepath String.
    * @param w Write Object.
    * @tparam T Parametrized Type.
    */
  def save[T](value: T, path: String)(implicit w: Writes[T]): Unit =
    save(value, path, None)

  /** Loads data.
    * @param path Filepath String.
    * @param key Option for Specification for Encryption.
    * @param r Reader Object.
    * @tparam T Parametrized Type.
    * @return Returns a object of Parametrized Type.
    */
  def load[T](path: String, key: Option[SecretKeySpec] = None)(implicit r: Reads[T]): T = {
    var file: Option[BufferedSource] = None
    try {
      file = Option(Source.fromFile(path))
      val data = file.get.mkString
      Json.parse(key.fold(data)(k => decrypt(k, data))).as[T]
    }
    finally {
      file.foreach(_.close())
    }
  }

  /** Loads data without Specification for Encryption.
    * @param path Filepath String.
    * @param r Reader Object.
    * @tparam T Parametrized Type.
    * @return Returns a object of Parametrized Type.
    */
  def load[T](path: String)(implicit r: Reads[T]): T =
    load(path, None)
}
