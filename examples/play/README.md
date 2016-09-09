Play with Elm sample application
================================

This application shows a simple counter maintained server-side and incremented client-side by Elm.

You can run the app in developer mode (`>sbt run`), then modify any listed file below and refresh your browser to see modifications.

There are several demonstration files available in this template.

Elm Application
===============

- ServerCounter.elm:

  Demonstrates how Elm code can call a Play controller.

- main.scala.html:

  Shows how a Play Twirl template can embed an Elm app, thus mixing UI rendered server and client side.

Controllers
===========

- HomeController.scala:

  Shows how to handle simple HTTP requests.

- CountController.scala:

  Shows how to inject a component into a controller and use the component when
  handling requests.

Components
==========

- Module.scala:

  Shows how to use Guice to bind all the components needed by your application.

- Counter.scala:

  An example of a component that contains state, in this case a simple counter.
