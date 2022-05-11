package io.lunes.core.storage

import java.io.File

import org.iq80.leveldb.{DB, Options}
import scorex.utils.ScorexLogging

/** Package Object for Persistency API  */
package object db extends ScorexLogging {
  /** opens a Database.
    * @param path Databse Path.
    * @param cacheSizeBites Cache size.
    * @param recreate Sets to true to recreate the Database (default is false).
    * @return Returns the Database object.
    */
  def openDB(path: String, cacheSizeBites: Long, recreate: Boolean = false): DB = {
    log.debug(s"Open DB at $path")
    val file = new File(path)
    val options = new Options()
      .createIfMissing(true)
      .paranoidChecks(true)
      .cacheSize(cacheSizeBites)

    if (recreate) {
      LevelDBFactory.factory.destroy(file, options)
    }

    file.getParentFile.mkdirs()
    LevelDBFactory.factory.open(file, options)
  }

}
