(ns tourbillon.workflow.jobs-test
  (:require [clojure.test :refer :all]
            [tourbillon.workflow.jobs :refer :all]
            [tourbillon.storage.object :refer [new-object-store find-by-id create! update!]]))

(def ^:dynamic *jobstore*)
(def ^:dynamic *workflowstore*)

(def stub-subscriber-system
  (reify
    tourbillon.workflow.subscribers/SubscriberSystem
    (notify-all! [_ _ _])))

(defn with-local-storage [test-fn]
  (let [jobstore (new-object-store {:type :local
                                    :db (atom {})
                                    :serialize-fn (partial into {})
                                    :unserialize-fn map->Job})
        workflowstore (new-object-store {:type :local
                                         :db (atom {})
                                         :serialize-fn (partial into {})
                                         :unserialize-fn map->Workflow})]
    (.start jobstore)
    (.start workflowstore)
    (binding [*jobstore* jobstore
              *workflowstore* workflowstore]
      (test-fn))
    (.stop workflowstore)
    (.stop jobstore)))

(use-fixtures :each with-local-storage)

(defn mk-test-event [id job]
  {:id id
   :job-id (:id job)
   :data {}})

(deftest test-local-jobstore
  (testing "Assigns id to job on create"
    (let [job (create-job nil nil [] nil)
          saved-job (create! *jobstore* job)]
      (is (not (nil? (:id saved-job))))))

  (testing "Can retrieve saved job"
    (let [saved-job (create! *jobstore* (create-job nil nil [] nil))
          retrieved-job (find-by-id *jobstore* (:id saved-job))]
      (is (= saved-job retrieved-job))))

  (testing "Updates existing job with function"
    (let [saved-job (create! *jobstore* (create-job nil nil [] nil))
          updated-job (update! *jobstore* saved-job
                                              #(update-in % [:states] conj :foo))
          retrieved-job (find-by-id *jobstore* (:id saved-job))]
      
      (is (= updated-job retrieved-job))
      (is (= :foo (-> updated-job :states first))))))

(deftest test-creating-jobs-from-workflows
  (testing "Creates job using workflow as blueprint"
    (let [workflow (create! *workflowstore* (create-workflow nil nil [] :a-state))
          job (Workflow->Job workflow)]
      (is (nil? (:id job)))
      (is (= :a-state (:current-state job))))))

;; TODO
(deftest updating-transitions
  (let [transitions [(create-transition :foo :bar "foo->bar")
                     (create-transition :bar :baz "bar->baz")]
        job (create-job nil nil transitions :foo)]
    
    (testing "Adds a new transition to a job"
      (let [transition (create-transition :foo :baz "foo->baz" [{:a "subscriber"}])
            updated-job (add-transition job transition)]
        (is (= updated-job (update-in job [:transitions] conj transition)))))

    (testing "Removes a transition from a job"
      (let [transition (create-transition :bar :baz "bar->baz")
            updated-job (remove-transition job transition)]
        (is (= updated-job (create-job nil nil [(create-transition :foo :bar "foo->bar")] :foo)))))

    (testing "Updates a transition's subscribers"
      (let [transition (assoc (first transitions) :subscribers {:some "subscriber"})
            updated-job (update-subscribers job transition)]
        (is (= transition (first (:transitions updated-job))))))))

(deftest test-state-transitioning
  (let [transitions [(create-transition :foo :bar "foo->bar")
                     (create-transition :bar :baz "bar->baz")]
        job (create! *jobstore* (create-job nil nil transitions :foo))]

    (testing "does not change state when event does not match transition"
      (let [new-job (emit! *jobstore* stub-subscriber-system (mk-test-event "bar->baz" job))]
        (is (= :foo (:current-state new-job)))))

    (testing "changes states when event matches transition"
      (let [new-job (emit! *jobstore* stub-subscriber-system (mk-test-event "foo->bar" job))]
        (is (= :bar (:current-state new-job)))))))

(deftest getting-current-state
  (let [transitions [(create-transition :foo :bar "foo->bar")
                     (create-transition :foo :baz "foo->baz")
                     (create-transition :bar :baz "bar->baz")]
        job (create! *jobstore* (create-job nil nil transitions :foo))]

    (testing "Gets all transitions for initial state"
      (is (= [{:to :bar :on "foo->bar" :subscribers []}
              {:to :baz :on "foo->baz" :subscribers []}]
             (get-current-transitions job))))

    (testing "Gets all transitions for non-initial-state state"
      (let [updated-job (assoc job :current-state :bar)]
        (is (= [{:to :baz :on "bar->baz" :subscribers []}]
               (get-current-transitions updated-job)))))

    (testing "Gets state and transitions"
      (is (= {:state :foo
              :transitions [{:to :bar :on "foo->bar" :subscribers []}
                            {:to :baz :on "foo->baz" :subscribers []}]}
             (get-current-state job))))))
