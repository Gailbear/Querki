package querki

import models.{Property, Thing}

import querki.ecology._
import querki.values.{QValue, SpaceState}

package object datamodel {
  
  val DataModelTag = "Data and Spaces"
  
  trait DataModelAccess extends EcologyInterface {
    def IsFunctionProp:Property[Boolean,Boolean]
    def CopyIntoInstances:Property[Boolean,Boolean]
    
    /**
     * Signal Value, to indicate a Value that has been intentionally removed.
     */
    def DeletedValue:QValue
    
    def isDeletable(t:Thing, allowIfProp:Boolean = false)(implicit state:SpaceState):Boolean
  }
}