(ns tourbillon.workflow.jobs
  (:require [tourbillon.workflow.subscribers :as subscribers]
            [tourbillon.storage.object :refer [find-by-id update!]]
            [tourbillon.domain :refer [Transition Job JobStatus Workflow Event]]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]
            [schema.core :as s]))

(defn- are-same-transition [& ts]
  (apply = (map (juxt :from :to :on) ts)))

(s/defn Workflow->Job :- Job
  [workflow :- Workflow]
  (-> workflow
      (dissoc :id)
      (rename-keys {:start-state :current-state})))

(s/defn get-valid-transition :- (s/maybe Transition)
  [job :- Job
   event :- Event]
  (let [transitions (:transitions job)
        current-state (:current-state job)
        event-id (:id event)]
    (first
      (filter #(and (= (:on %) event-id)
                    (= (:from %) current-state))
              transitions))))

(s/defn get-current-transitions :- [Transition]
  "Returns a list of all transitions possible from the current state."
  [job :- Job]
  (let [current-state (:current-state job)]
    (filterv #(= current-state (:from %))
             (:transitions job))))

(s/defn get-job-status :- JobStatus
  [job :- Job]
  {:state (:current-state job)
   :transitions (get-current-transitions job)})

(s/defn update-subscribers :- Job
  "Updates the job by replacing the transition with the same from, to, and on as the
  supplied transition with the supplied transition."
  [job :- Job
   transition :- Transition]
  (update-in job [:transitions]
    (fn [transitions]
      (map #(if (are-same-transition transition %) transition %)
           transitions))))

(s/defn add-transition :- Job
  [job :- Job
   transition :- Transition]
  (update-in job [:transitions] conj transition))

(s/defn remove-transition :- Job
  [job :- Job
   transition :- Transition]
  (update-in job [:transitions]
    (fn [transitions]
      (remove (partial are-same-transition transition) transitions))))

;; TODO: the responsibility of updating the job in the jobstore does not
;; seem natural here. See if there is a better place to refactor it.
;; Also, the notification of subscribers should probably be placed on a message queue
;; rather than simply passed off to a future.
(defn emit! [jobstore subscriber-system event]
  (when-let [job (find-by-id jobstore (:job-id event))]
    (if-let [transition (get-valid-transition job event)]
      (let [new-job (update! jobstore job #(assoc % :current-state (:to transition)))]
        (future
          (subscribers/notify-all! subscriber-system (:subscribers transition) (:data event)))
        new-job)
      job)))
