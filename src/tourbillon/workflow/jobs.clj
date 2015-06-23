(ns tourbillon.workflow.jobs
  (:require [tourbillon.workflow.subscribers :as subscribers]
            [tourbillon.storage.object :refer [find-by-id update!]]
            [taoensso.timbre :as log]
            [clojure.set :refer [rename-keys]]))

;; TODO: Create record type for Transition
(defn create-transition
  ([from to on] (create-transition from to on []))
  ([from to on subscribers] {:from from
                             :to to
                             :on on
                             :subscribers subscribers}))

(defrecord Workflow [id api-key transitions start-state])
(defn create-workflow [id api-key transitions start-state]
  (Workflow. id api-key transitions start-state))

(defrecord Job [id api-key transitions current-state])
(defn create-job [id api-key transitions current-state]
  (Job. id api-key transitions current-state))

(defn Workflow->Job [workflow]
  (-> workflow
    (into {})
    (dissoc :id)
    (rename-keys {:start-state :current-state})
    map->Job))

(defn get-valid-transition [job event]
  (let [transitions (:transitions job)
        current-state (:current-state job)
        event-id (:id event)]
    (first
      (filter #(and (= (:on %) event-id)
                    (= (:from %) current-state))
              transitions))))

(defn- are-same-transition [& ts]
  (apply = (map (juxt :from :to :on) ts)))

(defn get-current-transitions
  "Returns a list of all transitions possible from the current state.
  Transitions are represented as a map with :to, :on, and :subscriber keys."
  [job]
  (let [transitions (:transitions job)
        current-state (:current-state job)
        from-current (filter #(= current-state (:from %)) transitions)]
    (map #(select-keys % [:to :on :subscribers]) from-current)))

(defn get-current-state [job]
  {:state (:current-state job) :transitions (get-current-transitions job)})

(defn update-subscribers
  "Updates the job by replacing the transition with the same from, to, and on as the
  supplied transition with the supplied transition."
  [^Job job transition]
  (update-in job [:transitions]
    (fn [transitions]
      (map #(if (are-same-transition transition %) transition %)
           transitions))))

(defn add-transition [^Job job transition]
  (update-in job [:transitions] conj transition))

(defn remove-transition [^Job job transition]
  (update-in job [:transitions]
    (fn [transitions]
      (remove (partial are-same-transition transition) transitions))))

;; TODO: the responsibility of updating the job in the jobstore does not
;; seem natural here. See if there is a better place to refactor it.
(defn emit! [jobstore subscriber-system event]
  (when-let [job (find-by-id jobstore (:job-id event))]
    (if-let [transition (get-valid-transition job event)]
      (let [new-job (update! jobstore job #(assoc % :current-state (:to transition)))]
        (future
          (subscribers/notify-all! subscriber-system (:subscribers transition) (:data event)))
        new-job)
      job)))
