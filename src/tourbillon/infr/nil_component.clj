(ns tourbillon.infr.nil-component
  (:require [com.stuartsierra.component :as component]))            

(defrecord NilComponent []
  component/Lifecycle
  (start [component]
    component)    
    
  (stop [component]
    component))

(defn make-nil-component []
  (NilComponent.))
