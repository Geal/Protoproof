(ns protoproof.intercept
  (:refer-clojure :exclude [==])
  (:use [clojure.core.logic])
  (:use [clojure.core.logic.pldb])
  (:use [protoproof.transport])
)

(db-rel intercepter tr u)
(db-rel pass transport mallory message)
(db-rel mitm transport mallory message replaced)

(defn recv-mitm [transport bob message]
  (conde
    [(fresh [alice mallory]
      (sendmsg transport alice bob message)
      (pass transport mallory message)
    )]
    [(fresh [alice mallory original]
      (sendmsg transport alice bob original)
      (mitm transport mallory original message)
    )]
  )
)

(defn eavesdrop-mitm [tr m x]
  (fresh [a b]
    (intercepter tr m)
    (sendmsg tr a b x)
  )
)

(defmacro know-transport-mitm [u x]
  '(fresh [a tr]
    (transport tr)
    (generates a x)
    (conde
      ((recv-mitm tr u x))
      ((eavesdrop tr u x))
      ((eavesdrop-mitm tr u x))
    )
  )
)