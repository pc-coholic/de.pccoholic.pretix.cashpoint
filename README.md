# <img src="https://raw.githubusercontent.com/pc-coholic/de.pccoholic.pretix.cashpoint/master/img/web_hi_res_512.png" width="100" height="100" /> de.pccoholic.pretix.cashpoint

Pretix Cashdesk Android App

This project has been archived and should be used anymore.
==========================================================
At least in Germany and other countries requiring fiscal recording devices (TSE for example in DE), using this application to accept is illegal.
================================================================================================================================================
Please consider using [pretixPOS](https://pretix.eu/about/en/pos) instead, a proper, fully featured cashdesk system.
====================================================================================================================

## What is this?
This is a very rudimentary app to mark tickets as paid and print the the QR codes of the corresponding tickets using a Bluetooth printer.

## Compatible devices
pretixDPC is working in theory on any Android device running Lollipop 5.1 (API-Level 22).

However, at this point it relies heavily on the integrated barcode scanner of a device known as "Lecom U8000S" and "Caribe PL-40L". This depency is to be removed in future releases.

As for the supporter Bluetooth printers, the app has only been tested with the [ZJ-5802LD](http://www.zjiang.com/en/init.php/product/index?id=20). In theory, it should however work with any Bluetooth-enabled ESC/POS printer.

## Requirements
In order for this app zu work, you will need the [pretix-cashpoint](https://github.com/pc-coholic/pretix-cashpoint)-plugin.

## License

Copyright 2017 Martin Gross

Released under the terms of the Apache License 2.0
