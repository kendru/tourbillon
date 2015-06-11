(ns tourbillon.workflow.jobs
  (:require [tourbillon.workflow.subscribers :as subscribers]
            [tourbillon.storage.object :refer [find-by-id update!]]
            [clojure.set :refer [rename-keys]]))

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

;; TODO: the responsibility of updating the job in the jobstore does not
;; seem natural here. See if there is a better place to refactor it.
(defn emit! [jobstore event]
  (when-let [job (find-by-id jobstore (:job-id event))]
    (if-let [transition (get-valid-transition job event)]
      (let [new-job (update! jobstore job #(assoc % :current-state (:to transition)))]
        (subscribers/notify-all! (:subscribers transition) (:data event))
        new-job)
      job)))
