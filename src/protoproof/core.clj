(ns protoproof.core
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic])
  (:use [clojure.core.logic.pldb]))

(db-rel user p)

(def users
  (db
    [user 'Alice]
    [user 'Bob]
    [user 'Eve]
  )
)

(db-rel generates u x)

(db-rel transport tr)
(db-rel listener tr u)
(db-rel sender tr u)

(db-rel sendmsg tr a b x)

;(defn recv [tr u x]
;  (fresh[a]
;    (knows a x)
;    (sendmsg tr a u x)
;  )
;)

(defn eavesdrop [tr e x]
  (fresh [a b]
    (listener tr e)
    (sendmsg tr a b x)
  )
)

(db-rel gpow gx x)
;(defn knows [u x]
;  (conde
;    ((generates u x))
;    ((fresh [tr] (recv tr u x)))
;    ((fresh [tr] (eavesdrop tr u x)))
;  )
;)

; "calculate" knowledge: data obtained from operations
(defn knowc [u x]
  (conde
    ((generates u x))
    ((fresh [w]
      (generates u w)
      (gpow x w)
    ))
  )
)

; "transport" knwoledge
(defn knows [u x]
  (conde
    ((generates u x))
    ((fresh [a tr]
      (transport tr)
      (generates a x)
      (conde
        ((sendmsg tr a u x))
        (( eavesdrop tr u x))
      )
    ))
  )
)

(def knowledge
  (db
    [transport 'tr]
    [generates 'Alice 'abc]
    [generates 'Bob 'def2]
    [listener 'tr 'Eve]
    [sendmsg 'tr 'Alice 'Bob 'abc]
    [gpow 'y 'abc]
  )
)

(defn tst [q]
  (all (user q) (knows q 'def2))
)

(with-dbs [users knowledge]
  (run* [q] (all (user q) (knows q 'abc)))
)

(with-dbs [users knowledge]
  (run* [q] (all (fresh [x] (gpow x 'abc) (knowc q x))))
)
