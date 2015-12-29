(ns tourbillon.event.core
  (:require [taoensso.timbre :as log]
            [tourbillon.event.cron :as cron]
            [tourbillon.domain :refer [Event]]
            [clj-time.coerce :as time-coerce]
            [schema.core :as s]))

(def not-nil? (complement nil?))

(defprotocol Interval
  "A thing that recurs at at intervals"
  (succ [this from-timestamp]))

(extend-protocol Interval
  Integer
  (succ [this from-timestamp] (+ from-timestamp this))

  Long
  (succ [this from-timestamp] (+ from-timestamp this))

  String
  (succ [this from-timestamp]
    (/ (time-coerce/to-long
        (cron/get-next-time (time-coerce/from-long (* (inc from-timestamp) 1000))
                            (cron/parse-cron this)))
       1000))
  
  nil
  (succ [_ _] nil))

(s/defn create-event :- Event
  ([id job-id data] (create-event id job-id nil nil data))
  ([id job-id at data] (create-event id job-id at nil data))
  ([id job-id start interval data] {:id id
                                    :job-id job-id
                                    :start start
                                    :interval interval
                                    :data data}))

(s/defn is-immediate? :- Boolean
  [evt :- Event]
  (and (nil? (:start evt))
       (nil? (:interval evt))))

(s/defn is-future? :- Boolean
  [evt :- Event]
  (and (not-nil? (:start evt))
       (nil? (:interval evt))))

(s/defn is-recurring?  :- Boolean
  [evt :- Event]
  (and (not-nil? (:start evt))
       (not-nil? (:interval evt))))

(s/defn next-interval :- Event
  "Gets a new recurring Event representing the next occurrence
  of the given Event"
  [evt :- Event]
  (let [{:keys [start interval]} evt]
    (assoc evt :start (succ interval start))))

