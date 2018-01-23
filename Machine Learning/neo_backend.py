import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from flask import Flask
from flask import request
from sklearn.svm import SVC
from sklearn import metrics
from sklearn.externals import joblib
from sklearn.metrics import confusion_matrix
from sklearn.model_selection import train_test_split

#################################### Main node #############################################

app = Flask(__name__)


@app.route('/')
def index():
    return "Hello, World! - I'm Neo"

################################# Test your stuff ##########################################


'''
127.0.0.1:5000/neo/api/v1.0/test?
location=1&relieved=1&type=3&resting=130&maximum=140&bpm=76&peak_one=180&
peak_two=120&blood_pressure=80&angina=1&age=30&sex=1&hypertension=1
'''
@app.route('/neo/api/v1.0/test' , methods=['GET'] )
def test():

	params_list = []

	params_list.append(request.args['location'])
	params_list.append(request.args['relieved'])
	params_list.append(request.args['type'])
	params_list.append(request.args['resting'])
	params_list.append(request.args['maximum'])
	params_list.append(request.args['bpm'])
	params_list.append(request.args['peak_one'])
	params_list.append(request.args['peak_two'])
	params_list.append(request.args['blood_pressure'])
	params_list.append(request.args['angina'])
	params_list.append(request.args['age'])
	params_list.append(request.args['sex'])
	params_list.append(request.args['hypertension'])

	params_list_numpy = np.array(params_list)
 	
	loaded_model = joblib.load('neo_svm.pkl')
	svm_predictions = loaded_model.predict(params_list_numpy.reshape(1, -1))


	return str(svm_predictions)



################################# Get the Accuracy ###########################################


@app.route('/neo/api/v1.0/accuracy' , methods=['GET'])
def accuracy():
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
	    dataset.hypertension          # hypertension
	   ))
	 
	# dividing X, y into train and test data
	X_train, X_test, y_train, y_test = train_test_split(dataset, features, random_state = 0)
	 
	loaded_model = joblib.load('neo_svm.pkl')
	result = loaded_model.score(X_test, y_test)

	return str(dataset)

###################################################################################################


if __name__ == '__main__':
    app.run(debug=True)
