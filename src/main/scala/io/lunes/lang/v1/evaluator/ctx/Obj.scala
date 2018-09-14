package io.lunes.lang.v1.evaluator.ctx

import io.lunes.lang.v1.compiler.Terms.CASETYPEREF

case class CaseObj(caseType: CASETYPEREF, fields: Map[String, Any])
