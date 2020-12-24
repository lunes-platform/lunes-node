package io.lunes

import scorex.account.PrivateKeyAccount
import scorex.crypto.hash.{Blake2b256, Keccak256}
import scorex.crypto.signatures._

/**
  * Scorex Cryptography Main Object. Provides methods for hashing and Sigin.
  */
package object crypto {
  val SignatureLength: Int = Curve25519.SignatureLength

  val DigestSize: Int = 32

  /**
    * Fast Hash Function.
    * @param m input Array key of Byte.
    * @return Array of Byte hash output.
    */
  def fastHash(m: Array[Byte]): Array[Byte] = Blake2b256.hash(m)

  /**
    * [[crypto.fastHash]] façade for Strings.
    * @param s input [[scala.String]]
    * @return Array of Byte hash output.
    */
  def fastHash(s: String): Array[Byte] = fastHash(s.getBytes())

  /**
    * Secure Hash Function.
    * @param m Inputs Array key of Byte.
    * @return Array of Byte hash output.
    */
  def secureHash(m: Array[Byte]): Array[Byte] = Keccak256.hash(Blake2b256.hash(m))

  /**
    * [[crypto.secureHash()]] façade for Strings.
    * @param s
    * @return
    */
  def secureHash(s: String): Array[Byte] = secureHash(s.getBytes())

  /**
    * Signin Hash Function.
    * @param account gives a [[scorex.account.PrivateKeyAccount]] object.
    * @param message returns a Array of Byte.
    * @return
    */
  def sign(account: PrivateKeyAccount, message: Array[Byte]): Array[Byte] =
    Curve25519.sign(PrivateKey(account.privateKey), message)

  /**
    *
    * @param privateKeyBytes
    * @param message
    * @return
    */
  def sign(privateKeyBytes: Array[Byte], message: Array[Byte]): Array[Byte] =
    Curve25519.sign(PrivateKey(privateKeyBytes), message)

  /**
    *
    * @param signature
    * @param message
    * @param publicKey
    * @return
    */
  def verify(signature: Array[Byte], message: Array[Byte], publicKey: Array[Byte]): Boolean =
    Curve25519.verify(Signature(signature), message, PublicKey(publicKey))

  /**
    *
    * @param seed
    * @return
    */
  def createKeyPair(seed: Array[Byte]): (Array[Byte], Array[Byte]) = Curve25519.createKeyPair(seed)
}
