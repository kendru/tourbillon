(defproject tourbillon "0.1.1"
  :description "Web service for managing application workflows"
  :url "https://github.com/kendru/tourbillon"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repl-options {:init-ns tourbillon.user}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [org.clojure/tools.namespace "0.2.10"]
                 [http-kit "2.1.18"]
                 [com.taoensso/timbre "3.4.0"]
                 [ring-server "0.4.0"]
                 [ring/ring-ssl "0.2.1"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-codec "1.0.1"]
                 [buddy/buddy-core "1.2.0"]
                 [buddy/buddy-hashers "1.2.0"]
                 [buddy/buddy-auth "1.4.1"]
                 [buddy/buddy-sign "1.5.0"]
                 [aero "1.1.2"]
                 [slingshot "0.12.2"]
                 [stencil "0.4.0"]
                 [compojure "1.3.4"]
                 [cheshire "5.5.0"]
                 [com.draines/postal "1.11.3"]
                 [com.novemberain/monger "3.0.0-rc1"]
                 [org.clojure/java.jdbc "0.7.0-beta5"]
                 [org.postgresql/postgresql "42.1.3"]
                 [com.jolbox/bonecp "0.8.0.RELEASE"]
                 [clj-time "0.10.0"]
                 [overtone/at-at "1.2.0"]
                 [prismatic/schema "1.0.4"]]

  :plugins [[speclj "3.2.0"]]

  :main tourbillon.core

  :uberjar-name "tourbillon-standalone.jar"

  :aliases {"build" ["do" ["clean"] ["compile"] ["uberjar"]]}

  ; :test-paths ["spec"]

  :profiles {:production {:ring {:open-browser? false
                                 :stacktraces? false,
                                 :auto-reload? false}}
             :uberjar {:aot :all}
             :dev {:dependencies [[ring-mock "0.1.5"]
                                  [ring/ring-devel "1.2.2"]
                                  [speclj "3.2.0"]
                                  [org.clojure/test.check "0.9.0"]]}})
