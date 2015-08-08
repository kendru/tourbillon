(ns tourbillon.template.core-spec
  (:require [speclj.core :refer :all]
            [com.stuartsierra.component :as component]
            [tourbillon.template.core :refer :all]
            [tourbillon.storage.object :refer [new-object-store]]))

(def valid-template "Hello, {{ name }}")
(def invalid-template "Hello, {{ name")
(def api-key "some-api-key")

(def ^:dynamic *store*)

(describe "Templating"

  (it "accepts a valid mustache template"
    (should-be valid-template? valid-template))

  (it "rejects an invalid mustache template"
    (should-not-be valid-template? invalid-template))
  
  (describe "storage/retrieval"
    (tags :local)
    (around [it]
            (binding [*store* (.start (new-object-store {:type :local
                                                         :db (atom {})}))]
              (it)
              (.stop *store*)))

    (it "returns an id on successful storage"
      (let [id (create-template! *store* api-key valid-template)]
        (should-be (some-fn number? string?) id)))

    (it "renders a stored template"
      (let [id (create-template! *store* api-key valid-template)]
        (should= "Hello, Fido" (render-template *store* id {"name" "Fido"}))))

    (it "throws an exception when invalid template given to create"
      (should-throw AssertionError (create-template! *store* api-key invalid-template)))))