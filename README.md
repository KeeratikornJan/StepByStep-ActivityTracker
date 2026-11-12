# StepByStep-ActivityTracker CS364 Assignment 2
แอปนับก้าวเดินและจำนวนครั้งในการเขย่า

## สมาชิกในกลุ่มและหน้าที่
- 6709650078 วรรยา จันทะคาม
  
  หน้าที่:
  Database Developer (SQLite)
  รับผิดชอบการพัฒนาระบบฐานข้อมูล SQLite สำหรับจัดเก็บและจัดการประวัติกิจกรรม โดยมีหน้าที่ดังนี้
  
  Insert Session (ข้อ 6): บันทึกจำนวนก้าว จำนวนครั้งที่เขย่า ระยะเวลา และวันที่ของกิจกรรมลง SQLite
  
  Query & Persistence (ข้อ 7): ดึงประวัติกิจกรรมที่บันทึกไว้ และทำให้ข้อมูลยังคงอยู่เมื่อปิดและเปิดแอปใหม่
  
  Delete & Recalculate (ข้อ 8): ลบประวัติกิจกรรมและคำนวณยอดรวมกับสถิติสูงสุดใหม่
  
  Database Testing: ทดสอบการทำงานของฐานข้อมูลบน Android Emulator
  
  ไฟล์ที่รับผิดชอบ:
  
  DBHelper.java
  
  DBHelperInstrumentedTest.java

- 6709650086 วันวิสา จันทะคาม

  **หน้าที่: UI / RecyclerView Developer** รับผิดชอบการออกแบบและพัฒนาส่วนติดต่อผู้ใช้ (UI) ของแอป StepByStep ในธีม Clean Light รวมถึงการแสดงประวัติกิจกรรมและรา   ละเอียดกิจกรรม โดยมีหน้าที่ดังนี้

  **RecyclerView List (ข้อ 9):** พัฒนาระบบแสดงประวัติกิจกรรมเป็นรายการ โดยดึงข้อมูลจากฐานข้อมูล SQLite ผ่าน DBHelper แสดงวันที่และเวลา จำนวนก้าว จำนวนครั้งที่เขย่า    ระยะเวลา และค่าเฉลี่ยก้าวต่อนาที พร้อมป้ายสถิติ NEW STEP RECORD และ NEW SHAKE RECORD

  **Item Details & Empty State (ข้อ 10):** พัฒนาหน้าต่างแสดงรายละเอียดกิจกรรม (Dialog) เมื่อกดเลือกรายการ และแสดงข้อความเมื่อยังไม่มีประวัติกิจกรรม

  **Main UI Design:** ออกแบบหน้าหลักของแอปในธีม Clean Light สีฟ้าอ่อน–ขาว–เขียวมิ้นต์ โดยจัดแสดงจำนวนก้าว จำนวนครั้งที่เขย่า ระยะเวลา ค่าเฉลี่ย ยอดสะสม และปุ่มบันทึก     กิจกรรม

  **UI Integration:** เชื่อม RecyclerView กับ DBHelper และจัดเตรียมองค์ประกอบ UI สำหรับเชื่อมต่อกับ Sensor และ Session Logic ของสมาชิกคนอื่น

  **UI Testing:** ทดสอบการ Build และการแสดงผล UI บน Android Emulator โดย Build ผ่านเรียบร้อยแล้ว

  **ไฟล์ที่รับผิดชอบ:**

  - ActivityAdapter.java
  - ActivityHistoryView.java
  - activity_main.xml
  - item_activity.xml
  - dialog_activity_detail.xml
  - layout_activity_history.xml
  - bg_activity_card.xml
  - bg_record_badge.xml
  - bg_footprint_circle.xml

- 6709650136 นายกันตณัฐ สุพัฒนานนท์

- 6709650177 นายกีรติกร จรรยาประภานนท์

- 6709650466 นายปรวีย์ คนหมั่น


## DEMO
