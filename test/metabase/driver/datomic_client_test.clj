(ns metabase.driver.datomic-client-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer :all]
            [matcher-combinators.matchers :as m]
            [metabase.driver.datomic-client :refer :all]
            [gini.datomic-harness :as harness]))

(defn db-of
  "Returns an empty dev-local DB with no schema."
  [& txs]
  (reduce harness/conj-tx (harness/test-db) txs))

(defn simple-schema [idents]
  (for [[ident vtype] idents]
    {:db/ident ident
     :db/valueType vtype
     :db/cardinality :db.cardinality/one}))

(deftest can-connect?-test
  (let [_ (harness/test-db)]
    (is (can-connect? harness/test-db-spec))))

(deftest derive-table-names-test
  (testing "infer 1 table from schema"
    (is (match?
         (m/in-any-order ["table"])
         (-> {:table/a1 :db.type/ref
              :table/a2 :db.type/ref}
             simple-schema
             db-of
             derive-table-names))))

  (testing "infer 2 table from schema"
    (is (match?
         (m/in-any-order ["table-1", "table-2"])
         (-> {:table-1/a :db.type/ref
              :table-1/b :db.type/ref
              :table-2/a :db.type/ref}
             simple-schema
             db-of
             derive-table-names)))))

(deftest describe-table-test
  (testing "infer attributes with type from given table"
    (is (match?
         (m/in-any-order [[:table-1/a :db.type/ref]
                          [:table-1/b :db.type/long]])
         (-> {:table-1/a :db.type/ref
              :table-1/b :db.type/long
              :table-2/a :db.type/string}
             simple-schema
             db-of
             (table-columns "table-1"))))))

(deftest guess-column-dest-test
  (testing "finds frequent attr namespace pointed to by ref attribute"
    (is (=
         "table-2"
         (-> {:table-1/a :db.type/ref
              :table-1/b :db.type/long
              :table-2/a :db.type/string}
             simple-schema
             (db-of [{:table-1/a "some table-2"
                      :table-1/b 1}
                     {:db/id "some table-2"
                      :table-2/a "val"}])
             (guess-column-dest "table-1" :table-1/a))))))
