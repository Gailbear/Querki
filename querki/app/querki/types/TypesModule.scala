package querki.types

import models.{Kind}
import models.Thing._

import models.system.IntType
import models.system.ExactlyOne
import models.system.{AppliesToKindProp, SystemProperty}
import models.system.OIDs.sysId

import querki.conventions.{PropDetails, PropSummary}

import modules.Module

class TypesModule(val moduleId:Short) extends Module {
  object MOIDs {
    // Old Things, moved to here
    val MinTextLengthOID = sysId(100)
    
    val MinIntValueOID = moid(1)
    val MaxIntValueOID = moid(2)
  }
  import MOIDs._
  
  /***********************************************
   * PROPERTIES
   ***********************************************/

  lazy val MinTextLengthProp = new SystemProperty(MinTextLengthOID, IntType, ExactlyOne,
    toProps(
      setName("Minimum Text Length"),
      AppliesToKindProp(Kind.Property),
      PropSummary("The minimum length allowed in this Text, Large Text or PlainText Property"),
      PropDetails("""If you add this meta-Property to your Text Property, it defines
          |the minimum length that will be accepted in user-entered text.
          |
          |The Text value will be trimmed of leading and trailing whitespace before this test,
          |so a Text composed entirely of spaces is still considered to be length 0. (Since for
          |most output purposes, leading and trailing spaces don't exist.)""".stripMargin)))
  
  lazy val MinIntValueProp = new SystemProperty(MinIntValueOID, IntType, ExactlyOne,
    toProps(
      setName("Minimum Number Value"),
      AppliesToKindProp(Kind.Property),
      PropSummary("The minimum value allowed in this Whole Number Property")))
  
  lazy val MaxIntValueProp = new SystemProperty(MaxIntValueOID, IntType, ExactlyOne,
    toProps(
      setName("Maximum Number Value"),
      AppliesToKindProp(Kind.Property),
      PropSummary("The maximum value allowed in this Whole Number Property")))
  
  override lazy val props = Seq(
    MinTextLengthProp
  )
}

object TypesModule {
}