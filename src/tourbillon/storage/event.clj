(ns tourbillon.storage.event
  (:require [com.stuartsierra.component :as component]
            [tourbillon.infr.serializer :as s]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [clojure.java.jdbc :as sql]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]
            [tourbillon.utils :as utils]
            [tourbillon.storage.sql-helpers :refer [select-row]]))


(defn exc-inc-range
  "Gets a numeric range from start (exclusive) to end (inclusive),
  with an optional step parameter"
  [start end]
  (range (inc start) (inc end)))


(defn mapcat-eager [f xs]
  (->> xs (map f) (reduce concat)))


(defn maybe-as-num
  "Returns the supplied input string as a BigInteger only if it is a
  totally numeric string."
  [s]
  (if (re-find #"^\d+$" s)
    (BigInteger. s)
    s))


(def quote-sql-ident (sql/quoted \"))


(defprotocol EventStore
  (store-event! [this event])
  (get-events [this timestamp]))

;; Note that retrieval is NOT an efficient operation - it is linear with the time
;; passed since the last check. This event store is meant for dev/testing purposes
;; only and should not be used in production. 
(defrecord InMemoryEventStore [last-check-time atom-db]
  component/Lifecycle
  (start [component]
    (log/info "Starting local event store")
    component)

  (stop [component]
    (log/info "Stopping local event store")
    component)

  EventStore
  (store-event! [_ event]
    (let [timestamp (:start event)
          db (:db atom-db)]
      (swap! db update-in [timestamp] conj event)))

  (get-events [_ timestamp]
    (let [db (:db atom-db)
          all-timestamps (exc-inc-range @last-check-time timestamp)
          events (mapcat-eager #(get @db % '()) all-timestamps)]
      (swap! db #(apply dissoc % all-timestamps))
      (reset! last-check-time timestamp)
      events)))

(defrecord MongoDBEventStore [collection mongo]
  component/Lifecycle
  (start [component]
    (log/info "Starting MongoDB event store")
    component)

  (stop [component]
    (log/info "Stopping MongoDB event store")
    component)

  EventStore
  (store-event! [this event]
    (let [timestamp (:start event)
          {:keys [db]} mongo]
      (mc/update db collection {:_id timestamp}
                 {$push {:events event}}
                 {:upsert true})))

  (get-events [this timestamp]
    (let [{:keys [db]} mongo
          records (mc/find-and-modify db collection
                                      {:_id {$lte timestamp}}
                                      {}
                                      {:remove true})]
      (mapcat :events records))))


(defrecord PostgresEventStore [table postgres serializer]
  component/Lifecycle
  (start [component]
    (log/info "Starting Postgres event store (" table ")")
    component)

  (stop [component]
    (log/info "Stopping Postgres event store(" table ")")
    component)

  EventStore
  (store-event! [this event]
    (let [{:keys [id job-id start interval data]} event
          {:keys [conn]} postgres]
      (sql/insert! conn table {:id id
                               :job_id job-id
                               :start start
                               :interval (str interval)
                               :data (s/serialize serializer data)})))

  (get-events [this timestamp]
    (let [query (str "UPDATE " (quote-sql-ident table)
                     " SET is_expired = true"
                     " WHERE \"start\" <= ?"
                     " AND is_expired = false"
                     " RETURNING id, job_id, \"start\", \"interval\", data, iv")
          {:keys [conn]} postgres
          events (sql/query conn [query timestamp])]
      (mapv (fn [row]
              (-> row
                  (update-in [:data] #(s/deserialize serializer %))
                  (update-in [:interval] maybe-as-num)
                  (rename-keys {:job_id :job-id})))
            events))))

(defmulti make-store
  (fn [cfg]
    (get-in cfg [:global :event-store-type])))

(defmethod make-store :local
  [_]
  (map->InMemoryEventStore
    {:last-check-time (atom (utils/get-time))}))

(defmethod make-store :mongo
  [_]
  (map->MongoDBEventStore
    {:collection "events"}))

(defmethod make-store :postgres
  [_]
  (map->PostgresEventStore
    {:table "events"}))
