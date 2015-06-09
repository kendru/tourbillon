(ns tourbillon.event.store
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [tourbillon.utils :as utils]))

(defn exc-inc-range
  "Gets a numeric range from start (exclusive) to end (inclusive),
  with an optional step parameter"
  [start end]
  (reverse (range end start -1)))

;; TODO make this polymorphic so that different stores implement
;; a protocol with store-event! and get-events
(defrecord LocalEventStore [map-atom last-check]
  component/Lifecycle
  (start [component]
    (log/info "Starting local event store")
    (assoc component :type ::local
                     :map-atom map-atom
                     :last-check last-check))

  (stop [component]
    (log/info "Stopping local event store")
    (dissoc component :map-atom :type :last-check)))

(defn store-event! [store event]
  (let [timestamp (:start event)
        map-atom (:map-atom store)]
    (swap! map-atom update-in [timestamp] conj event)))

; TODO: we may need to change the reference type wrapping the store
; to a ref and run this in a transaction
(defn get-events [store timestamp]
  (let [map-atom (:map-atom store)
        last-check (:last-check store)
        all-timestamps (exc-inc-range @last-check timestamp)
        events (mapcat #(get @map-atom % (list)) all-timestamps)]
    (println "Getting events in " all-timestamps)
    (swap! map-atom #(apply dissoc % all-timestamps))
    (reset! last-check timestamp)
    events))

(defn new-store
  ([map-atom] (new-store map-atom (utils/get-time)))
  ([map-atom last-check] (map->LocalEventStore {:map-atom map-atom
                                                :last-check (atom last-check)})))
