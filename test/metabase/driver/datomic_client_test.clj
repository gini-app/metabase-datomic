(ns metabase.driver.datomic-client-test
  (:require [clojure.test :refer :all]
            [metabase.driver.datomic-client :as dc-driver]

            [gini.datomic-harness :as dh]))

(deftest can-connect?-test
  (is (dc-driver/can-connect?
       ;; TODO(alan) use dev-local db
       {:endpoint "localhost:8998"
        :access-key "k"
        :secret "s"
        :db-name "m13n"})))

(deftest derive-table-names-test
  (testing "infer table-names from schema"
    (is true
        #_dc-driver/derive-table-names)))

(deftest describe-table-test
  (testing "infer attributes of given table"
    (is true
        #_dc-driver/describe-table )))
