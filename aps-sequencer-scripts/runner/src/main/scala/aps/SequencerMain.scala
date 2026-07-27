package aps

object SequencerMain {
  def main(args: Array[String]): Unit = {
    esw.ocs.app.SequencerApp.main(args)
    Thread.currentThread().join()
  }
}
