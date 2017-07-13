(defproject tourbillon "0.1.1"
  :description "web service for managing application workflows"
  :url "https://github.com/kendru/tourbillon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repl-options {:init-ns tourbillon.user}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [org.clojure/tools.namespace "0.2.10"]
                 [http-kit "2.1.18"]
                 [com.taoensso/timbre "3.4.0"]
                 [ring-server "0.4.0"]
                 [ring/ring-ssl "0.2.1"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-codec "1.0.1"]
                 [buddy/buddy "0.5.5"]
                 [slingshot "0.12.2"]
                 [stencil "0.4.0"]
                 [crypto-random "1.2.0"]
                 [compojure "1.3.4"]
                 [cheshire "5.5.0"]
                 [com.draines/postal "1.11.3"]
                 [com.novemberain/monger "3.0.0-rc1"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [org.postgresql/postgresql "9.4-1201-jdbc41"]
                 [com.mchange/c3p0 "0.9.2.1"]
                 [environ "1.0.0"]
                 [clj-time "0.10.0"]
                 [overtone/at-at "1.2.0"]
                 [prismatic/schema "1.0.4"]]

  :plugins [[lein-environ "1.0.0"]
            [speclj "3.2.0"]]

  :main tourbillon.main

  :aliases {"build" ["do" ["clean"] ["compile"] ["uberjar"]]}

  :test-paths ["spec"]

  :profiles {:production {:ring {:open-browser? false
                                 :stacktraces? false,
                                 :auto-reload? false}}
             :uberjar {:aot :all}
             :dev {:dependencies [[ring-mock "0.1.5"]
                                  [ring/ring-devel "1.2.2"]
                                  [speclj "3.2.0"]
                                  [org.clojure/test.check "0.9.0"]]}}

;; In production, some of these will be overridden by environment variables
:env {
      ;; Global/environment
      :app-env "dev"
      :object-store-type "local"
      :event-store-type "local"

      ;; Web server
      :web-ip "0.0.0.0"
      :web-port "3300"

      ;; Storage
      :mongo-host "127.0.0.1"
      :mongo-db "tourbillon"
      :mongo-collection-workflows "workflows"
      :mongo-collection-jobs "jobs"
      :mongo-collection-events "events"

      :sql-classname "org.postgresql.Driver"
      :sql-subprotocol "postgresql"
      :sql-host "127.0.0.1"
      :sql-port "6543"
      :sql-database "tourbillon"
      :sql-user "tourbillon"
      :sql-password "s3cr3t"

      ;; Security
      :hmac-secret "s3cr3t"

      ;; Email subscriber
      :smtp-host "REPLACEME"
      :smtp-user "REPLACEME"
      :smtp-pass "REPLACEME"
      :smtp-port "REPLACEME"
      :smtp-sender "REPLACEME"

      ;; SMS subscriber
      :twilio-sid "REPLACEME"
      :twilio-auth-token "REPLACEME"
      :twilio-sender "REPLACEME"

      ;; Misc
      :log-file "/var/log/tourbillon.log"
      })
