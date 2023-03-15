/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

        private val _navigateToSleepQuality = MutableLiveData<SleepNight>()
        val navigateToSleepQuality: LiveData<SleepNight> get() = _navigateToSleepQuality
        fun doneNavigating(){
                _navigateToSleepQuality.value = null
        }

        private val _navigateToSleepData = MutableLiveData<Long>()
        val navigateToSleepData: LiveData<Long> get() = _navigateToSleepData
        fun onSleepNightClicked(id: Long){
                _navigateToSleepData.value = id
        }
        fun onSleepDataNavigated(){
                _navigateToSleepData.value = null
        }

        val viewModelJob: Job = Job()

        override fun onCleared() {
                super.onCleared()
                viewModelJob.cancel()
        }

        private val _snackBarStatus = MutableLiveData<Boolean>()
        val snackBarStatus: LiveData<Boolean> get() = _snackBarStatus
        fun snackBarDone(){
                _snackBarStatus.value = false
        }

        val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

        private var tonight = MutableLiveData<SleepNight>()

        val nights = database.getAllNights()
        var nightsString = Transformations.map(nights){ nights ->
                formatNights(nights, application.resources)
        }

        val startButtonVisible = Transformations.map(tonight){
                null == it
        }

        val stopButtonVisible = Transformations.map(tonight){
                null != it
        }

        val clearButtonVisible = Transformations.map(nights){
                it?.isNotEmpty()
        }

        init {
            initializeTonight()
        }

        private fun initializeTonight(){
                uiScope.launch {
                        tonight.value = getTonightFromDatabase()
                }
        }

        private suspend fun getTonightFromDatabase(): SleepNight?{

                return withContext(Dispatchers.IO){
                        var night = database.getTonight()
                        if(night?.end_time_millis != night?.start_time_milli){
                                night = null
                        }
                        night
                }
        }

        fun onStartTracking(){

                uiScope.launch {
                        var newNight = SleepNight()
                        insert(newNight)
                        tonight.value = getTonightFromDatabase()
                }
        }

        private suspend fun insert(newNight: SleepNight){
                withContext(Dispatchers.IO){
                        database.insertSleepNight(newNight)
                }
        }

        fun onStopTracking(){

                uiScope.launch {
                        val oldNight = tonight.value ?: return@launch
                        oldNight.end_time_millis = System.currentTimeMillis()
                        update(oldNight)

                        _navigateToSleepQuality.value = oldNight
                }
        }

        private suspend fun update(oldnight: SleepNight){

                withContext(Dispatchers.IO){
                        database.updateSleepNight(oldnight)
                }
        }

        fun onClear(){
         uiScope.launch{
                 clear()
                 tonight.value = null
                 _snackBarStatus.value = true
         }
        }

        private suspend fun clear(){
                withContext(Dispatchers.IO){
                        database.clearAllNights()
                }
        }

}

