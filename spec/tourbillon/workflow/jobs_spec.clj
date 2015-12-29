(ns tourbillon.workflow.jobs-spec
  (:require [speclj.core :refer :all]
            [schema.experimental.generators :as g]
            [schema.experimental.complete :as c]
            [tourbillon.workflow.jobs :refer :all]
            [tourbillon.storage.object :refer [new-object-store find-by-id create! update!]]
            [tourbillon.domain :refer [Job Workflow]]))

(def ^:dynamic *jobstore*)
(def ^:dynamic *workflowstore*)

(def stub-subscriber-system
  (reify
    tourbillon.workflow.subscribers/SubscriberSystem
    (notify-all! [_ _ _])))

(defn mk-test-event [id job-id]
  {:id id
   :job-id job-id
   :data {}})

(describe "Jobs"
  (tags :local)
  (around [it]
          (binding [*jobstore* (.start (new-object-store {:type :local
                                                          :db (atom {})
                                                          :schema Job}))
                    *workflowstore* (.start (new-object-store {:type :local
                                                               :db (atom {})
                                                               :schema Workflow}))]
            (it)
            (.stop *workflowstore*)
            (.stop *jobstore*)))

  (it "assigns an id to a job on create"
      (let [job (g/generate Job)
            saved-job (create! *jobstore* job)]
        (should-not-be nil? (:id saved-job))))

  (it "retrieves a saved job"
      (let [saved-job (create! *jobstore* (dissoc (g/generate Job) :id))
            retrieved-job (find-by-id *jobstore* (:id saved-job))]
        (should= saved-job retrieved-job)))

  (it "updates an existing job"
    (let [saved-job (create! *jobstore* (dissoc (g/generate Job) :id))
          updated-job (update! *jobstore* saved-job
                                              #(update-in % [:states] conj :foo))
          retrieved-job (find-by-id *jobstore* (:id saved-job))]
      
      (should= updated-job retrieved-job)
      (should= :foo (-> updated-job :states first))))

  (describe "creation from Workflows"
    (it "uses a workflow as a blueprint to create a job"
        (let [workflow (create! *workflowstore* (g/generate Workflow))
              job (Workflow->Job workflow)]
          (should-be nil? (:id job))
          (should= (:start-state workflow) (:current-state job)))))

  (describe "updating transitions"
            (with transitions [{:from "foo"
                                :to "bar"
                                :on "foo->bar"}
                               {:from "bar"
                                :to "bax"
                                :on "bar->baz"}])
            (with job {:transitions @transitions
                       :current-state "foo"})

    (it "adds a new transition to a job"
        (let [transition {:from "foo"
                          :to "baz"
                          :on "foo->baz"
                          :subscribers [{:a "subscriber"}]}
            updated-job (add-transition @job transition)]
        (should= updated-job (update-in @job [:transitions] conj transition))))

    (it "removes a transition from a job"
        (should= {:transitions [(first @transitions)]
                  :current-state "foo"}
                 (remove-transition @job (second @transitions))))

    (it "updates a transition's subscribers"
        (let [transition (assoc (first @transitions) :subscribers {:some "subscriber"})
              updated-job (update-subscribers @job transition)]
          (should= transition (first (:transitions updated-job))))))

  (describe "transitioning states"
            (with transitions [{:from "foo"
                                :to "bar"
                                :on "foo->bar"}
                               {:from "bar"
                                :to "baz"
                                :on "bar->baz"}])
            (with job (merge (g/generate Job) {:transitions @transitions :current-state "foo"}))

    (it "changes state when event matches transition"
        (let [job (create! *jobstore* @job)
              new-job (emit! *jobstore* stub-subscriber-system (mk-test-event "foo->bar" (:id job)))]
          (should= "bar" (:current-state new-job))))

    (it "does not change state when event does not match transition"
        (let [job (create! *jobstore* @job)
              new-job (emit! *jobstore* stub-subscriber-system (mk-test-event "bar->baz" (:id job)))]
          (should= "foo" (:current-state new-job)))))

  (describe "retrieving state"
            (with transitions transitions [{:from "foo" :to "bar" :on "foo->bar" :subscribers []}
                                           {:from "foo" :to "baz" :on "foo->baz" :subscribers []}
                                           {:from "bar" :to "baz" :on "bar->baz" :subscribers []}])
            (with job (merge (g/generate Job) {:transitions @transitions :current-state "foo"}))

    (it "gets all transitions for initial state"
      (should= [{:from "foo" :to "bar" :on "foo->bar" :subscribers []}
                {:from "foo" :to "baz" :on "foo->baz" :subscribers []}]
               (get-current-transitions @job)))

    (it "gets all transitions for non-initial-state state"
      (let [updated-job (assoc @job :current-state "bar")]
        (should= [{:from "bar" :to "baz" :on "bar->baz" :subscribers []}]
                 (get-current-transitions updated-job))))

    (it "gets state and transitions"
      (should= {:state "foo"
                :transitions [{:from "foo" :to "bar" :on "foo->bar" :subscribers []}
                              {:from "foo" :to "baz" :on "foo->baz" :subscribers []}]}
               (get-job-status @job)))))
