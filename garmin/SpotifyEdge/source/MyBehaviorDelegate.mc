using Toybox.System;
using Toybox.WatchUi;

class MyBehaviorDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        WatchUi.BehaviorDelegate.initialize();
    }

    function onKey(keyEvent) {
        var menu = new WatchUi.Menu();

        menu.addItem("Next song", :nextSong);
        menu.addItem("Play/Pause", :playPause);
        menu.addItem("Like/Unlike song", :likeUnlikeSong);
        menu.addItem("Update UI", :updateUi);

        WatchUi.pushView(menu, new MyMenuDelegate(), SLIDE_IMMEDIATE);

        return true;
    }

    function onTap(clickEvent) {
        var listener = new MyConnectionListener();
        Communications.transmit("nextSong", null, listener);
        return true;
    }

}