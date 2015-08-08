(ns tourbillon.workflow.jobs-spec
  (:require [speclj.core :refer :all]
            [tourbillon.workflow.jobs :refer :all]
            [tourbillon.storage.object :refer [new-object-store find-by-id create! update!]]))

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
                                                          :serialize-fn (partial into {})
                                                          :unserialize-fn map->Job}))
                    *workflowstore* (.start (new-object-store {:type :local
                                                               :db (atom {})
                                                               :serialize-fn (partial into {})
                                                               :unserialize-fn map->Workflow}))]
            (it)
            (.stop *workflowstore*)
            (.stop *jobstore*)))

  (it "assigns an id to a job on create"
    (let [job (create-job nil nil [] nil)
          saved-job (create! *jobstore* job)]
      (should-not-be nil? (:id saved-job))))

  (it "retrieves a saved job"
    (let [saved-job (create! *jobstore* (create-job nil nil [] nil))
          retrieved-job (find-by-id *jobstore* (:id saved-job))]
      (should= saved-job retrieved-job)))

  (it "updates an existing job"
    (let [saved-job (create! *jobstore* (create-job nil nil [] nil))
          updated-job (update! *jobstore* saved-job
                                              #(update-in % [:states] conj :foo))
          retrieved-job (find-by-id *jobstore* (:id saved-job))]
      
      (should= updated-job retrieved-job)
      (should= :foo (-> updated-job :states first))))

  (describe "creation from Workflows"
    (it "uses a workflow as a blueprint to create a job"
      (let [workflow (create! *workflowstore* (create-workflow nil nil [] :a-state))
            job (Workflow->Job workflow)]
        (should-be nil? (:id job))
        (should= :a-state (:current-state job)))))

  (describe "updating transitions"
    (with transitions [(create-transition :foo :bar "foo->bar")
                       (create-transition :bar :baz "bar->baz")])
    (with job (create-job nil nil @transitions :foo))

    (it "adds a new transition to a job"
      (let [transition (create-transition :foo :baz "foo->baz" [{:a "subscriber"}])
            updated-job (add-transition @job transition)]
        (should= updated-job (update-in @job [:transitions] conj transition))))

    (it "removes a transition from a job"
        (should= (remove-transition @job (second @transitions))
                 (create-job nil nil [(first @transitions)] :foo)))

    (it "updates a transition's subscribers"
        (let [transition (assoc (first @transitions) :subscribers {:some "subscriber"})
              updated-job (update-subscribers @job transition)]
          (should= transition (first (:transitions updated-job))))))

  (describe "transitioning states"
    (with transitions [(create-transition :foo :bar "foo->bar")
                       (create-transition :bar :baz "bar->baz")])

    (it "changes state when event matches transition"
      (let [job (create! *jobstore* (create-job nil nil @transitions :foo))
            new-job (emit! *jobstore* stub-subscriber-system (mk-test-event "foo->bar" (:id job)))]
        (should= :bar (:current-state new-job))))

    (it "does not change state when event does not match transition"
      (let [job (create! *jobstore* (create-job nil nil @transitions :foo))
            new-job (emit! *jobstore* stub-subscriber-system (mk-test-event "bar->baz" (:id job)))]
        (should= :foo (:current-state new-job)))))

  (describe "retrieving state"
    (with transitions transitions [(create-transition :foo :bar "foo->bar")
                                   (create-transition :foo :baz "foo->baz")
                                   (create-transition :bar :baz "bar->baz")])
    (with job (create-job nil nil @transitions :foo))

    (it "gets all transitions for initial state"
      (should= [{:to :bar :on "foo->bar" :subscribers []}
                {:to :baz :on "foo->baz" :subscribers []}]
               (get-current-transitions @job)))

    (it "gets all transitions for non-initial-state state"
      (let [updated-job (assoc @job :current-state :bar)]
        (should= [{:to :baz :on "bar->baz" :subscribers []}]
                 (get-current-transitions updated-job))))

    (it "gets state and transitions"
      (should= {:state :foo
                :transitions [{:to :bar :on "foo->bar" :subscribers []}
                              {:to :baz :on "foo->baz" :subscribers []}]}
               (get-current-state @job)))))
