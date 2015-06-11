(ns tourbillon.templates.core-test
  (:require [clojure.test :refer :all]
            [tourbillon.template.core :refer :all]
            [tourbillon.storage.object :refer [new-object-store]]))

(def valid-template "Hello, {{ name }}")
(def invalid-template "Hello, {{ name")
(def api-key "some-api-key")

(def ^:dynamic *store*)

(defn with-store [test-fn]
  (let [store (new-object-store {:type :local :db (atom {})})]
    (binding [*store* (.start store)]
      (test-fn))
    (.stop store)))

(use-fixtures :each with-store)

(deftest test-check-valid-template
  (testing "Accepts a valid mustache template"
    (is (valid-template? valid-template)))

  (testing "Rejects an invalid mustache template"
    (is (not (valid-template? invalid-template))))

  (testing "Throws exception when invalid template given to create"
    (is (thrown? AssertionError (create-template! *store* api-key invalid-template)))))

(deftest storing-and-getting-templates
  (testing "Storing returns an id"
    (let [id (create-template! *store* api-key valid-template)]
      (is (or (number? id)
              (string? id)))))

  (testing "Renders a stored template"
    (let [id (create-template! *store* api-key valid-template)]
      (is (= "Hello, Fido" (render-template *store* id {"name" "Fido"}))))))