# 20 Jan 2018 - Health Monitor
# Using Support Vector Machine to predict heart disease as a part of a bigger project


# importing required libraries
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from sklearn.svm import SVC
from sklearn import metrics
from sklearn.externals import joblib
from sklearn.metrics import confusion_matrix
from sklearn.model_selection import train_test_split

# reading csv file and extracting class column to y.
dataset = pd.read_csv("data.csv")
dataset.fillna(dataset.mean(), inplace=True)

dataset_to_array = np.array(dataset)
features = dataset_to_array[:,57] # "Target" classes having 0 and 1
features = features.astype('int')

# extracting 13 features
dataset = np.column_stack((
    dataset_to_array[:,4] ,       # pain location
    dataset_to_array[:,6] ,       # relieved after rest
    dataset_to_array[:,9] ,       # pain type 
    dataset_to_array[:,11],       # resting blood pressure
    dataset_to_array[:,33],       # maximum heart rate achieved
    dataset_to_array[:,34],       # resting heart rate 
    dataset_to_array[:,35],       # peak exercise blood pressure (first of 2 parts) 
    dataset_to_array[:,36],       # peak exercise blood pressure (second of 2 parts) 
    dataset_to_array[:,38],       # resting blood pressure 
    dataset_to_array[:,39],       # exercise induced angina (1 = yes; 0 = no) 
    dataset.age,                  # age 
    dataset.sex ,                 # sex
    dataset.hypertension          # hyper tension
   ))

print "This Dataset has (Rows , Features) : " , dataset.shape , "\n"
 
#print (x), "\n\n" , (y)
 
# dividing X, y into train and test data
X_train, X_test, y_train, y_test = train_test_split(dataset, features, random_state = 0)
 
svm_model_linear = SVC(kernel = 'linear', C = 1).fit(X_train, y_train)
svm_predictions = svm_model_linear.predict(X_test)
 
# model accuracy for X_test  
#accuracy = svm_model_linear.score(X_test, y_test)
accuracy = metrics.accuracy_score(y_test, svm_predictions)
print "Accuracy of the model is :" , accuracy , "\nApproximately : ", round(accuracy*100) , "%\n"

# creating a confusion matrix
cm = confusion_matrix(y_test, svm_predictions)
print "Confusion Matrix :\n\n" , cm , "\n"


joblib.dump(svm_model_linear, 'neo_svm.pkl')
