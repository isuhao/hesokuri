(ns hesokuri.util
  (:use [clojure.java.shell :only [sh]]
        [clojure.string :only [trim join]]))

(defmacro lint-and
  "Performs a short-circuited logical int-based and. If the first expression is
  0, then the next expression is not evaluated. Returns the last expression
  evaluated."
  [x y]
  (let [x-res (gensym)]
    `(let [~x-res ~x]
       (if (= 0 ~x-res) ~x-res ~y))))

(defmacro lint-or
  "Performs a short-circuited logical int-based or. If the first expression is
  non-zero, then the next expression is not evaluated. Returns the last
  expression evaluated."
  [x y]
  (let [x-res (gensym)]
    `(let [~x-res ~x]
       (if (not= 0 ~x-res) ~x-res ~y))))

(defn sh-print-when
  "Runs the command-line given in args using clojure.java.shell/sh. Returns
  the exit code. If the 'print-when' argument (a function) returns truthy when
  passed the result of sh, it prints out the stderr and stdout of the process to
  this process' stderr and stdout."
  [print-when & args]
  (let [result (apply sh args)]
    (when (print-when result)
      (println (join " " args))
      (.write *err* (:err result))
      (print (:out result)))
    (:exit result)))

(defn sh-print
  [& args]
  (apply sh-print-when (constantly true) args))

(defn is-ff!
  "Returns true iff the second hash is a fast-forward of the first hash. When
  the hashes are the same, returns when-equal."
  [source-dir from-hash to-hash when-equal]
  (if (= from-hash to-hash) when-equal
      (= from-hash (trim (:out (sh "git" "merge-base"
                                   from-hash to-hash :dir source-dir))))))

(defrecord PeerRepo [host path]
  Object
  (toString [_]
    (str "ssh://" host path)))

(defn start-heartbeat
  "Gives functionality to run a repeated task at regular intervals, and stops
  when stop-heartbeats is called. self should be an atom."
  [self interval-millis action]
  (let [orig-hb-group @self]
    (-> (fn [] (while (= orig-hb-group @self)
                 (action)
                 (Thread/sleep interval-millis)))
        Thread.
        .start))
  self)

(defn stop-heartbeats
  "Stops all heartbeats begun on the given atom with start-heartbeat."
  [self]
  (swap! self (fn [_] (Object.)))
  self)

(defmacro letmap
  "A macro that behaves like let, creating temporary bindings for variables, and
  also creates a map containing the bindings. An abbreviated form is supplied
  which simply evaluates to the map, which is useful for creating maps where
  some entries are used to calculate other entries. For instance, the
  abbreviated form:
    (letmap [a 10, b (* a 20)])
  evaluates to:
    {:a 10, :b 20}

  In the full form, the symbol immediately after the macro name is the name of
  the map that can be used in the let body:
    (letmap m [a 10, b (* a 20)]
      (into m [[:c m]]))
  evaluates to:
    {:a 10, :b 200, :c {:a 10, :b 200}}"
  ([map-name bindings & body]
     `(let ~bindings
        (let [~map-name
              ~(into {} (for [b (take-nth 2 bindings)] [(keyword b) b]))]
          ~@body)))
  ([bindings]
     (let [map-name (gensym)]
       `(letmap ~map-name ~bindings ~map-name))))