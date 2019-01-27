# -*- coding: utf-8 -*-

import os,shutil

cur_path = os.getcwd()

for i in range(1,32):
    shutil.copy("template.md", "2019-01-%02d.md"% i)