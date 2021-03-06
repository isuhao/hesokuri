; Copyright (C) 2013 Google Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns hesokuri.test-hesokuri.util
  (:use clojure.test
        hesokuri.util
        hesokuri.test-hesokuri.mock))

(defmacro sh-print-tests
  [& body]
  `(let [result# (atom {})

         ~'with-printed
         (fn [func# args#]
           (swap! result# #(dissoc % :printed :return))
           (let [return# (apply func# args#)]
             (swap! result# #(assoc % :return return#))))]
     (binding [*sh* #(if (< %1 %2)
                       {:exit 1 :err "err" :out "out"}
                       {:exit 0
                        :err (format "err: %d" (- %1 %2))
                        :out (format "out: %d" (- %1 %2))})

               *print-for-sh*
               (fn [args# stderr# stdout#]
                 (swap! result# #(assoc % :printed {:args args#
                                                    :stderr stderr#
                                                    :stdout stdout#})))]
       ~@body)))

(deftest test-sh-print-when
  (sh-print-tests
   (are [args exp-return exp-stderr exp-stdout]
        (= (with-printed sh-print-when args)
           {:return exp-return
            :printed {:args (rest args)
                      :stderr exp-stderr
                      :stdout exp-stdout}})
        [(constantly true) 5 4] 0 "err: 1" "out: 1"
        [(constantly true) 5 6] 1 "err" "out"
        [#(= (:exit %) 0) 1 0] 0 "err: 1" "out: 1"
        [#(= (:exit %) 1) 0 1] 1 "err" "out")
   (are [args exp-return]
        (= (with-printed sh-print-when args) {:return exp-return})
        [(constantly false) 5 4] 0
        [(constantly false) 5 6] 1
        [#(= (:exit %) 0) 0 1] 1
        [#(= (:exit %) 1) 1 0] 0)))

(deftest test-sh-print
  (sh-print-tests
   (are [args exp-return exp-stderr exp-stdout]
        (= (with-printed sh-print args)
           {:return exp-return
            :printed {:args args
                      :stderr exp-stderr
                      :stdout exp-stdout}})
        [5 4] 0 "err: 1" "out: 1"
        [5 6] 1 "err" "out")))

(deftest test-is-ff
  (let [sh-result (fn [output] (repeat 10 {:out output :exit 0}))
        sh (mock {["git" "merge-base" "a" "b" :dir :srcdir] (sh-result "c")
                  ["git" "merge-base" "b" "a" :dir :srcdir] (sh-result "c")
                  ["git" "merge-base" "d" "e" :dir :srcdir] (sh-result "e")
                  ["git" "merge-base" "e" "d" :dir :srcdir] (sh-result "e")
                  ["git" "merge-base" "f" "g" :dir :srcdir] (sh-result "f")})]
    (binding [*sh* sh]
      (are [from-hash to-hash when-equal res]
           (= (boolean res)
              (boolean (is-ff! :srcdir from-hash to-hash when-equal)))
           "a" "b" nil false
           "b" "a" nil false
           "d" "d" true true
           "d" "e" nil false
           "e" "d" nil true))))

(deftest test-peer-repo
  (are [host path combined]
       (= combined (str (->PeerRepo host path)))
       "foo" "/bar" "ssh://foo/bar"
       "" "" "ssh://"
       "foo.bar" "/" "ssh://foo.bar/"))

(deftest letmap-omitted-key
  (binding [*letmap-omitted-key* ::omitted]
    (is (= {:foo 42 ::omitted {:bar 1011 :baz 314}}
           (letmap
            [:omit baz 314
             foo 42
             :omit bar 1011])))
    (is (= {:foo 42 ::omitted {}}
           (letmap [foo 42])))))
