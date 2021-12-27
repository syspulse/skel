#!/bin/bash

ammonite gas.sc | python3 -c "import plotext as plt; import pandas; df = pandas.read_csv('/dev/stdin'); plt.bar(df['gwei'], df['pending'], orientation = 'v'); plt.xfrequency(25); plt.plot_size(200, 45); plt.show()"
