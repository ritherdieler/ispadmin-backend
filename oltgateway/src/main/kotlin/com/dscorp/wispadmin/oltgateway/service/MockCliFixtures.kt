package com.dscorp.wispadmin.oltgateway.service

object MockCliFixtures {
    const val DISPLAY_VERSION = """
  VERSION : MA5600V800R015C00
  PATCH   : SPH106
  PRODUCT : MA5608T
  UPTIME  : 120 day(s), 5 hour(s), 32 minute(s), 10 second(s)
MA5608T#
"""

    const val DISPLAY_BOARD_0 = """
  Board Name  : H805GPFD
  Board Status: Normal
  0        H805GPFD     Normal
MA5608T#
"""

    const val DISPLAY_BOARD_1 = """
  Board Name  : H806GPFD
  Board Status: Normal
  1        H806GPFD     Normal
MA5608T#
"""

    const val DISPLAY_AUTOFIND = """
Number of autofind ONTs: 2

  F/S/P                  : 0/0/2
  Ont SN                 : 4857544311E70E9A
  VendorID               : HWTC
  Ont EquipmentID        : HG8245H

  F/S/P                  : 0/1/0
  Ont SN                 : ALCLFCA81B0E
  VendorID               : ALCL
  Ont EquipmentID        : G-140W-C
MA5608T#
"""

    const val DISPLAY_BY_SN = """
  0/1/0   5        cliente_demo
  Control flag      : active
  Run state         : online
  Description       : cliente_demo
  SN                : 4857544311E70E9A
  Line profile ID   : 10
  Line profile name : line-profile_10
  Service profile ID: 10
  Service profile name: srv-profile_10
MA5608T#
"""

    const val DISPLAY_SUMMARY = """
  0/1/0     0   HWTC11E70E9A  active      online   success  match    no
  0/1/0     1   ALCLFCA81B0E  active      online   success  match    no
MA5608T#
"""

    const val DISPLAY_DETAIL = DISPLAY_BY_SN

    const val DISPLAY_OPTICAL = """
  5         -18.234   2.145     -               45.12        3.280    15.000
MA5608T#
"""
}
