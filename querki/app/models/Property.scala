package models

import language.existentials

import Thing.{PropFetcher, PropMap}

import ql.{PartiallyAppliedFunction, QLFunction, QLPhrase}

import querki.core.MOIDs._
import querki.ecology._

import querki.time.DateTime
import querki.types.Types

import querki.values._

/**
 * A Property is a field that may exist on a Thing. It is, itself, a Thing with a
 * specific Type.
 */
case class Property[VT, -RT](
    i:OID, 
    s:OID, 
    m:OID, 
    val pType:PType[VT] with PTypeBuilder[VT, RT], 
    val cType:Collection, 
    pf:PropFetcher,
    mt:DateTime)(implicit e:Ecology) 
  extends Thing(i, s, m, Kind.Property, pf, mt)(e)
{
  lazy val DefaultValueProp = interface[Types].DefaultValueProp
  def WarningValue(msg:String) = interface[querki.ql.QL].WarningValue(msg)
    
  def default = {
    val explicitDefault = localProp(DefaultValueProp).map(_.v)
    explicitDefault.getOrElse(cType.default(pType))
  }
  def defaultPair:PropAndVal[VT] = PropAndVal(this, default)
  // EVIL but arguably necessary. This is where we are trying to confine the cast from something
  // we get out of the PropMap (which is a bit undertyped) to match the associated Property.
  def castVal(v:QValue) = v.asInstanceOf[QValue]
  def pair(v:QValue) = PropAndVal(this, castVal(v))

  override lazy val props:PropMap = propFetcher() + 
		  (CollectionPropOID -> Core.ExactlyOne(Core.LinkType(cType))) +
		  (TypePropOID -> Core.ExactlyOne(Core.LinkType(pType)))

  /**
   * This little method is a type-math workaround. We're often dealing with properties in contexts
   * where we know the PType of the property (and thus, the VT) for external reasons, but the
   * Scala compiler isn't smart enough to figure that out. So this provides a consistent way to
   * do the cast safely, at runtime. 
   */
  def confirmType[PVT](pt:PType[PVT]):Property[PVT,_] = {    
    if (pt == pType)
      this.asInstanceOf[Property[PVT,_]]
    else
      throw new Exception("confirmType called on wrong type! Expected " + pType + ", but received " + pt)
  }
  
  /**
   * This renders the Property itself, if it has no DisplayText defined.
   */
  override def renderDefault(implicit request:RequestContext):Wikitext = {
    val fromType = pType.renderProperty(this)
    fromType.getOrElse(renderProps)
  }
  
  def from(m:PropMap):QValue = castVal(m(this))
  def fromOpt(m:PropMap):Option[QValue] = m.get(this.id) map castVal
  
  /**
   * Convenience method to fetch the value of this property in this map.
   */
  def first(m:PropMap):VT = pType.get(cType.first(from(m)))
  def firstOpt(m:PropMap):Option[VT] = fromOpt(m) map cType.first map pType.get

  def apply(raws:RT*) = (this.id, QValue.make(cType, pType, raws:_*))
  def apply() = (this.id, cType.default(pType))
  def apply(qv:QValue) = (this.id, qv)
  
  def validate(str:String, state:SpaceState) = pType.validate(str, this, state)
  import play.api.data.Form
  def fromUser(on:Option[Thing], form:Form[_], state:SpaceState):FormFieldInfo = cType.fromUser(on, form, this, pType, state)
  // TODO: this clearly isn't correct. How are we actually going to handle more complex types?
  def toUser(v:QValue):String = {
    val cv = castVal(v)
    if (cType.isEmpty(cv))
      ""
    else
      pType.toUser(cType.first(cv))
  }
  
  def serialize(v:QValue):String = v.serialize(pType)
  def deserialize(str:String):QValue = cType.deserialize(str, pType)
  
  def applyToIncomingThing(context:QLContext)(action:(Thing, QLContext) => QValue):QValue = {
    if (context.isEmpty) {
      EmptyValue(pType)
    } else {
      val valType = context.value.pType
      valType match {
        case link:querki.core.TypeCreation#LinkType => {
          val coll = context.value.cType
          val thing = link.followLink(context)
          thing match {
            case Some(t) => action(t, context)
            case None => WarningValue("Couldn't find Thing from " + context.toString)
          }
        }
        case _ => WarningValue("Can't apply a Property in a " + valType.displayName + " context!")
      }
    }
  }
  
  /**
   * By default, qlApply on a Property expects the input context to be a single Link. It returns the value
   * of this Property on that Link.
   * 
   * TODO: if this Property isn't defined on the target Thing or its ancestors, this should return None.
   * So technically, this should be returning Optional. Note that PType.qlApply() already does this.
   */
  override def qlApply(context:QLContext, params:Option[Seq[QLPhrase]] = None):QValue = {
    // Give the Type first dibs at handling the call; otherwise, return the value of this property
    // on the incoming thing.
    pType.qlApplyFromProp(context, context, this, params).getOrElse(
      applyToIncomingThing(context) { (t, innerContext) =>
        t.getPropVal(this)(innerContext.state)
      })
  }  
  
  override def partiallyApply(leftContext:QLContext):QLFunction = {
    def handleRemainder(mainContext:QLContext, params:Option[Seq[QLPhrase]]):QValue = {
      // Note that partial application ignores the incoming context if the type isn't doing anything clever. By
      // and large, this syntax mainly exists for QL properties:
      //
      //   incomingContext -> definingThing.MyQLFunction(params)
      //
      // But we allow you to use partial application in general, since it sometimes feels natural.
      pType.qlApplyFromProp(leftContext, mainContext, this, params).getOrElse(applyToIncomingThing(leftContext) { (t, context) =>
        t.getPropVal(this)(context.state)
      })
    }
    new PartiallyAppliedFunction(leftContext, handleRemainder)
  }
}

class FieldIds(t:Option[Thing], p:Property[_,_]) {
  lazy val propId = p.id.toString
  lazy val thingId = t.map(_.id.toString).getOrElse("")
  lazy val suffix = "-" + propId + "-" + thingId  
  
  lazy val inputControlId = "v" + suffix
  lazy val collectionControlId = "coll" + suffix
  // This is a hidden input field, which is a flag to tell the receiving code whether the
  // field is "empty" -- inherited or deleted, but with no local value:
  lazy val emptyControlId = "empty" + suffix
}
object FieldIds {
  def apply(t:Option[Thing], p:Property[_,_]) = new FieldIds(t,p)
}

case class DisplayPropVal(on:Option[Thing], prop: Property[_,_], v: Option[QValue], inheritedVal:Option[QValue] = None, inheritedFrom:Option[Thing] = None) extends
  FieldIds(on, prop)
{
  lazy val isInherited = v.isEmpty && inheritedVal.isDefined
  
  lazy val hasInheritance = inheritedVal.isDefined
  
  lazy val effectiveV = v.orElse(inheritedVal)
}
