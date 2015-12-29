(ns tourbillon.domain
  (:require [schema.core :as s]))

;; A state transition a state machine graph
(def Transition
  {:from String
   :to String
   :on String
   :subscribers [{String s/Any}]})

;; A state machine data type
(def Job
  {:id (s/maybe String)
   :api-key String
   :current-state String
   :transitions [Transition]})

;; A description of the current state of a Job and the transitions
;; that could move it to a next state
(def JobStatus
  {:state String
   :transitions [Transition]})

;; A blueprint for creating Job state machines
(def Workflow
  {:id (s/maybe String)
   :api-key String
   :start-state String
   :transitions [Transition]})

;; An action that can exist at one or more points in time
(def Event
  {:id String
   :job-id String
   :start (s/maybe Long)
   :interval (s/maybe (s/cond-pre s/Int String))
   :data {String s/Any}})

