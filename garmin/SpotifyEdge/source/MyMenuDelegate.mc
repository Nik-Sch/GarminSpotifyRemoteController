using Toybox.System;
using Toybox.WatchUi;
using Toybox.Communications;

class MyMenuDelegate extends WatchUi.MenuInputDelegate {
    function initialize() {
        WatchUi.MenuInputDelegate.initialize();
    }

    function onMenuItem(item) {
        var listener = new MyConnectionListener();

        if(item == :nextSong) {
            Communications.transmit("nextSong", null, listener);
        } else if(item == :playPause) {
            Communications.transmit("playPause", null, listener);
        } else if(item == :likeUnlikeSong) {
            Communications.transmit("likeUnlikeSong", null, listener);
        } else if(item == :updateUi) {
            Communications.transmit("sendPlayerInfo", null, listener);
        }
    }
}