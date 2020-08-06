(ns metabase.driver.datomic-test
  (:require [clojure.test :refer :all]
            [metabase.driver :as driver]
            [metabase.driver.datomic :as mbd]))

(deftest describe-database-test
  (testing "m13n database"
    (is
     (driver/describe-database
      :datomic
      {:details {:db "datomic:dev://localhost:4334/m13n"}})

     {:tables #{{:name "merchant", :schema nil}
                {:name "operator", :schema nil}
                {:name "rule", :schema nil}
                {:name "txn", :schema nil}
                {:name "txn.descr", :schema nil}}})))

(deftest describe-table-test
  (testing "merchant table"
    (is
     (driver/describe-table
      :datomic
      {:details {:db "datomic:dev://localhost:4334/m13n"}}
      {:name "merchant"})

     {:name "merchant"
      :schema nil
      :fields #{{:base-type :type/Boolean,
                 :database-type "db.type/boolean",
                 :name "approved",
                 :special-type :type/Boolean}
                {:base-type :type/DateTime,
                 :database-type "db.type/instant",
                 :name "created-at",
                 :special-type :type/DateTime}
                {:base-type :type/DateTime,
                 :database-type "db.type/instant",
                 :name "updated-at",
                 :special-type :type/DateTime}
                {:base-type :type/PK,
                 :database-type "db.type/ref",
                 :name "db/id",
                 :pk? true}
                {:base-type :type/Text,
                 :database-type "db.type/string",
                 :name "id",
                 :special-type :type/Text}
                {:base-type :type/Text,
                 :database-type "db.type/string",
                 :name "name-en",
                 :special-type :type/Text}
                {:base-type :type/Text,
                 :database-type "db.type/string",
                 :name "name-zh",
                 :special-type :type/Text}
                {:base-type :type/URL,
                 :database-type "db.type/uri",
                 :name "logo",
                 :special-type :type/URL}}})))
