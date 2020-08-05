(defproject metabase/datomic-driver "0.32.10.mb-0.9.63.dc-SNAPSHOT"
  :min-lein-version "2.5.0"

  :plugins [[lein-tools-deps "0.4.3"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:aliases [:metabase]
                           :config-files [:install :project]}

  :profiles
  {:provided
   {:dependencies [[metabase-core "1.0.0-SNAPSHOT"]]}

   :datomic-free
   {:lein-tools-deps/config {:aliases [:datomic-free]}}

   :datomic-client
   {:lein-tools-deps/config {:aliases [:datomic-client]}
    :repositories
    {"my.datomic.com" {:url "https://my.datomic.com/repo"}}}

   :datomic-pro
   {:lein-tools-deps/config {:aliases [:datomic-pro]}
    :repositories
    {"my.datomic.com" {:url "https://my.datomic.com/repo"}}}

   :uberjar
   {:auto-clean    true
    :aot           :all
    :javac-options ["-target" "1.8", "-source" "1.8"]
    :target-path   "target/%s"
    :uberjar-name  "datomic.metabase-driver.jar"}})
