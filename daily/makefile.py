# -*- coding: utf-8 -*-

import os,shutil

cur_path = os.getcwd()

for i in range(2,31):
    shutil.copy("daily/2018-05-01.md", "daily/2018-06-%02d.md"% i)