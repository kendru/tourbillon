(ns tourbillon.infr.atom-db
  (:require [com.stuartsierra.component :as component]
            [tourbillon.infr.nil-component :refer [make-nil-component]]
            [taoensso.timbre :as log]))

(defrecord AtomDb []
  component/Lifecycle
  (start [component]
    (log/info "Starting local atom db connection")
    (assoc component :db (atom {})))
    
  (stop [component]
    (log/info "Stopping local atom db connection")
    (assoc component :db nil)))

(defn make-db [config]
  (if (= :local (get-in config [:global :object-store-type]))
    (AtomDb.)
    (make-nil-component)))
