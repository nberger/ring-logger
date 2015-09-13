# Changelog

## 0.7.1

New features:

* Add `:timing` option to disable logging timing information

Breaking changes:

* The options `:pre-logger`, `:post-logger` and `:exception-logger` were removed
  They were intended as a way to override the way the messages are generated,
  but we now have multimethods and `:printer` to do that.



## 0.7.0

The goal for this release was to refactor ring-logger and remove the timbre & onelog dependencies.

Breaking changes:

* Remove timbre implementation, was moved to ring-logger-timbre
* Remove OneLog implementation, was moved to ring-logger-onelog
* Replace keyword arguments with a proper map in wrap-with-logger
* Replace :logger-impl param to :logger
* Make Logger protocol more simple: It has just two fns now: `log` and `add-extra-middleware`

## 0.6.2

* Fix OneLog implementation: error -> error-with-ex

## 0.6.1

* Add ability to customize messages & to disable coloring

## 0.6.0

* Adds tools.logging and taoensso/timbre implementations, with the ability to change the
implementation by passing a :logger-impl. The default is tools.logging now
