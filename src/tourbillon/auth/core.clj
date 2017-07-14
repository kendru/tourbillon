(ns tourbillon.auth.core
  (:require [buddy.auth :as auth]
            [buddy.auth.accessrules :refer [success error]]
            [buddy.auth.backends.token :refer [jws-backend]]
            [clj-time.core :as time]))

(def default-permissions #{"get-jobs" "get-workflows" "create-jobs" "create-workflows" "create-events" "get-templates" "create-templates"})


(defn get-api-claim [details]
  (-> details
      (dissoc :secret-digest)
      (assoc :exp (time/plus (time/now) (time/hours 1)))
      (clojure.set/rename-keys {:id :api-key})))


;; Auth handlers

(def any-access (constantly (success)))

(defn authenticated-key
  [req]
  (if (auth/authenticated? req)
    (success)
    (error "Must be authenticated")))

(defn restrict-to [permission]
  {:and [authenticated-key
         (fn [req]
           (let [api-key (get-in req [:identity :api-key])
                 permissions (into #{} (get-in req [:identity :permissions]))]
             (if (contains? permissions permission)
               (success)
               (error (str  permission " not allowed for api key: " api-key)))))]})

(defn on-error [req msg]
  {:status 403
   :headers {}
   :body {:status "error"
          :message msg}})

(defn backend [signer]
  (jws-backend {:secret (:secret signer)
                :unauthorized-handler on-error}))
