


def check_year(year):
  if(1900<=int(year)<=2023):
    return True
  else:
    print("잘못됨 다시입력")
    return False
  
def check_year2(year1, year2):
  if int(year1) > int(year2):
    print("잘못됨 다시입력")
    return False
  else:
    return True


year1 = 0
while True:
  try:
    year1 = int(input("Enter the first number: "))
    if check_year(year1):
      break
  except:
    print("잘못된 입력입니다. 정수를 입력하세요.")

while True:
  try:
    year2 = int(input("Enter the second number: "))
    if check_year2(year1, year2):
      break
  except:
    print("잘못된 입력입니다. 정수를 입력하세요.")

print(year2-year1,"주년입니다")
